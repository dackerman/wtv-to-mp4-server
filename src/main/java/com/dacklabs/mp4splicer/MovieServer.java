package com.dacklabs.mp4splicer;

import com.dacklabs.mp4splicer.model.EncodingStats;
import com.dacklabs.mp4splicer.model.Job;
import com.dacklabs.mp4splicer.model.JobStatus;
import com.dacklabs.mp4splicer.templateengines.ExternalJadeTemplateEngine;
import com.dacklabs.mp4splicer.templateengines.ResourcesJadeTemplateEngine;
import com.dacklabs.mp4splicer.workers.FFMpegConcatWorker;
import com.dacklabs.mp4splicer.workers.FFMpegFilterGraphWorker;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.MultimapBuilder;
import spark.ModelAndView;
import spark.QueryParamsMap;
import spark.Spark;
import spark.TemplateEngine;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class MovieServer {
    public static void main(String[] argsArray) throws IOException {
        Iterator<String> args = Lists.newArrayList(argsArray).iterator();
        boolean debug = false;
        String tempDirPath = null;
        int port = 4567;
        String ffmpeg = null;
        while (args.hasNext()) {
            String flag = args.next();
            switch (flag) {
                case "-d":
                    debug = true;
                    break;
                case "-tmpDir":
                    Preconditions.checkArgument(args.hasNext(), "Need to specify tmpDir");
                    tempDirPath = args.next();
                    break;
                case "-port":
                    Preconditions.checkArgument(args.hasNext(), "Need to specify -port <theport>");
                    port = Integer.valueOf(args.next());
                case "-ffmpeg":
                    Preconditions.checkArgument(args.hasNext(), "Need to specify ffmpeg path after -ffmpeg");
                    ffmpeg = args.next();
            }
        }
        final String ffmpegPath = ffmpeg;
        Preconditions.checkNotNull(tempDirPath, "Specify a location to put intermediate files with -tmpDir");
        Preconditions.checkNotNull(ffmpegPath, "Specify the path to the ffmpeg executable with -ffmpeg");
        final File tempDir = new File(tempDirPath);
        TemplateEngine templateEngine = new ResourcesJadeTemplateEngine();
        if (debug) {
            System.out.println("Debugging");
            templateEngine = new ExternalJadeTemplateEngine();
        }

        if (!Files.exists(Paths.get("logs/"))) {
            Files.createDirectory(Paths.get("logs/")); // create logs directory if it doesn't exist
        }

        ExecutorService executorService = Executors.newCachedThreadPool();

        ListMultimap<String, Process> runningProcesses = MultimapBuilder.hashKeys().arrayListValues().build();

        Database db = new Database("job-database");

        for (Job job : db.jobs()) {
            if (!job.status.equals(JobStatus.DONE) && !job.status.equals(JobStatus.CANCELED)) {
                System.out.println("Restarting incomplete job " + job.jobID);
                executorService.submit(createWorker(tempDir, ffmpegPath, runningProcesses, db, job));
            }
        }
        Spark.port(port);

        Spark.staticFileLocation("public");

        Spark.get("/", (req, res) -> {
            Map<String, Object> map = new HashMap<>();
            List<Job> jobs = db.jobs();
            Collections.sort(jobs, Job.COMPARATOR);
            List<Double> completionPercentages = jobs.stream().map(j -> {
                List<EncodingStats> concatStats = db.getJobStats(j);
                EncodingStats currentStats = Iterables.getLast(concatStats, EncodingStats.none());
                return j.percentComplete(currentStats);
            }).collect(Collectors.toList());
            map.put("jobs", jobs);
            map.put("completionPercentages", completionPercentages);
            return new ModelAndView(map, "main");
        }, templateEngine);

        Spark.get("/jobs/:jobId", (req, res) -> {
            String jobId = req.params("jobId");
            Job job = db.getJob(jobId);
            List<EncodingStats> outputStats = db.getJobStats(job);
            EncodingStats currentOutputStats = Iterables.getLast(outputStats, EncodingStats.none());

            Map<String, Object> map = new HashMap<>();
            map.put("job", job);
            map.put("percentComplete", job.percentComplete(currentOutputStats));
            map.put("outputStats", currentOutputStats);
            return new ModelAndView(map, "job");
        }, templateEngine);

        Spark.get("/jobs/:jobId/cancel", (req, res) -> {
            String jobID = req.params("jobId");
            Job job = db.saveJob(db.getJob(jobID).cancel());
            for (Process process : runningProcesses.get(job.jobID)) {
                process.destroyForcibly();
            }

            res.redirect("/jobs/" + jobID);
            return "";
        });

        Spark.get("/logs/:jobId", (req, res) -> {
            String jobID = req.params("jobId");
            Job job = db.getJob(jobID);
            BufferedReader reader =
                    Files.newBufferedReader(Paths.get(job.jobStdErrFile()), Charsets.UTF_8);
            PrintWriter writer = res.raw().getWriter();
            char[] buf = new char[10000];
            while (reader.ready()) {
                int size = reader.read(buf);
                if (size == -1) break;
                writer.write(buf, 0, size);
            }
            reader.close();
            res.type("text/plain");
            writer.flush();
            return null;
        });

        Spark.get("/browse", (req, res) -> {res.redirect("/browse/C:||Temp"); return null;});

        Spark.get("/browse/:url", (req, res) -> {
            String url = req.params("url");
            String path = toPath(url);
            File file = new File(path);
            List<FrontendFile> directoryFiles = new ArrayList<>();
            if (file.isDirectory() && file.canRead()) {
                File[] files = file.listFiles();
                directoryFiles = Arrays.asList(files).stream()
                                       .sorted((f1, f2) -> Boolean.compare(f2.isDirectory(), f1.isDirectory()))
                                       .map(MovieServer::frontendFile).collect(Collectors.toList());
                File parent = file.getParentFile();
                if (parent != null && parent.exists()) {
                    directoryFiles.add(0, frontendFile(parent, ".."));
                }
            } else {
                throw new RuntimeException("Couldn't read directory " + file.getAbsolutePath());
            }
            Map<String, Object> listings = new HashMap<>();
            listings.put("directory", path);
            listings.put("listings", directoryFiles);
            return new ModelAndView(listings, "listings");
        }, templateEngine);

        Spark.post("/create-job", (req, res) -> {
            QueryParamsMap jobDetails = req.queryMap();
            String name = Preconditions.checkNotNull(jobDetails.get("name").value(), "job name cannot be null");
            String outputFile =
                    Preconditions.checkNotNull(jobDetails.get("output").value(), "output file name cannot be null");
            String directory =
                    Preconditions.checkNotNull(jobDetails.get("directory").value(), "Base directory cannot be null");
            boolean goFast = jobDetails.get("fast").value() != null;
            String[] inputFiles = jobDetails.get("inputFiles").values();
            Integer startTrim = getTrim("startTrim", jobDetails);
            Integer endTrim = getTrim("endTrim", jobDetails);

            Preconditions.checkArgument(inputFiles.length > 1, "Must have at least two inputs");
            String jobId = UUID.randomUUID().toString();
            Job job = Job.create(jobId, name, directory, outputFile, Arrays.asList(inputFiles), startTrim, endTrim,
                                 goFast);
            db.saveJob(job);
            executorService.submit(createWorker(tempDir, ffmpegPath, runningProcesses, db, job));

            res.redirect("/");
            return null;
        });
    }

    private static Runnable createWorker(File tempDir, String ffmpegPath, ListMultimap<String, Process> runningProcesses,
                                         Database db, Job job) {
        if (job.goFast) {
            return new FFMpegConcatWorker(db, runningProcesses, job.jobID, tempDir.getAbsolutePath(), ffmpegPath);
        } else {
            return new FFMpegFilterGraphWorker(db, runningProcesses, job.jobID, tempDir.getAbsolutePath(), ffmpegPath);
        }
    }

    private static Integer getTrim(String name, QueryParamsMap queryParamsMap) {
        Integer hours = parseNullableInt(queryParamsMap.get(name + "Hours").value());
        Integer minutes = parseNullableInt(queryParamsMap.get(name + "Minutes").value());
        Integer seconds = parseNullableInt(queryParamsMap.get(name + "Seconds").value());
        Long amount = Duration.of(hours != null ? hours.longValue() : 0, ChronoUnit.HOURS)
                .plusMinutes(minutes != null ? minutes.longValue() : 0)
                .plusSeconds(seconds != null ? seconds.longValue() : 0)
                .getSeconds();
        return amount == 0 ? null : amount.intValue();
    }

    private static Integer parseNullableInt(String val) {
        if (val == null || val.trim().isEmpty()) {
            return null;
        }
        return Integer.parseInt(val.trim());
    }

    private static FrontendFile frontendFile(File f) {
        return frontendFile(f, null);
    }

    private static FrontendFile frontendFile(File f, String alternateName) {
        String url = fromPath(f.getAbsolutePath());
        String path = f.getAbsolutePath();
        String name = alternateName != null ? alternateName : f.getName();
        boolean isDirectory = f.isDirectory();
        boolean isWtv = f.getName().endsWith(".wtv");
        return new FrontendFile(url, path, name, isDirectory, isWtv);
    }

    private static String toPath(String url) {
        return Joiner.on("/").join(url.split("\\|"));
    }

    private static String fromPath(String path) {
        return "/browse/" + Joiner.on("|").join(path.split("\\\\"));
    }

    public static class FrontendFile {
        public final String url;
        public final String path;
        public final String name;
        public final boolean isDirectory;
        public final boolean isWtv;

        private FrontendFile(String url, String path, String name, boolean isDirectory, boolean isWtv) {
            this.url = url;
            this.path = path;
            this.name = name;
            this.isDirectory = isDirectory;
            this.isWtv = isWtv;
        }
    }
}
