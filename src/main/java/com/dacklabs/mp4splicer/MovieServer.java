package com.dacklabs.mp4splicer;

import com.dacklabs.mp4splicer.model.EncodingStats;
import com.dacklabs.mp4splicer.model.Job;
import com.dacklabs.mp4splicer.model.JobStatus;
import com.dacklabs.mp4splicer.templateengines.ExternalJadeTemplateEngine;
import com.dacklabs.mp4splicer.templateengines.ResourcesJadeTemplateEngine;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.MultimapBuilder;
import spark.*;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class MovieServer {
    public static void main(String[] argsArray) throws IOException {
        System.out.println("Starting");
        Iterator<String> args = Lists.newArrayList(argsArray).iterator();
        boolean debug = false;
        File tempDir = null;
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
                    tempDir = new File(args.next());
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
        Preconditions.checkNotNull(tempDir, "Specify a location to put intermediate files with -tmpDir");
        Preconditions.checkNotNull(ffmpegPath, "Specify the path to the ffmpeg executable with -ffmpeg");
        String tempPath = tempDir.getAbsolutePath();
        TemplateEngine templateEngine = new ResourcesJadeTemplateEngine();
        if (debug) {
            System.out.println("Debugging");
            templateEngine = new ExternalJadeTemplateEngine();
        }

        ThreadPoolExecutor executor =
                new ThreadPoolExecutor(1, 5, 60, TimeUnit.SECONDS, new LinkedBlockingDeque<>());

        ListMultimap<String, Process> runningProcesses = MultimapBuilder.hashKeys().arrayListValues().build();

        Database db = new Database("job-database");

        for (Job job : db.jobs()) {
            if (!job.status.equals(JobStatus.DONE) && !job.status.equals(JobStatus.CANCELED)) {
                System.out.println("Restarting incomplete job " + job.jobID);
                executor.execute(new FFMpegConcatWorker(db, runningProcesses, job.jobID, tempDir.getAbsolutePath(),
                                                        ffmpegPath));
            }
        }
        Spark.port(port);

        Spark.staticFileLocation("public");

        System.out.println("Setting up routes");
        Spark.get("/", (req, res) -> {
            Map<String, Object> map = new HashMap<>();
            map.put("jobs", db.jobs());
            return new ModelAndView(map, "main");
        }, templateEngine);

        Spark.get("/jobs/:jobId", (req, res) -> {
            String jobId = req.params("jobId");
            Job job = db.getJob(jobId);
            List<EncodingStats> inputStats = new ArrayList<>();
            for (int i = 0; i < job.inputPaths.size(); i++) {
                inputStats.add(Iterables.getLast(db.getInputStats(job, i), EncodingStats.none()));
            }

            Map<String, Object> map = new HashMap<>();
            map.put("job", job);
            map.put("inputStats", inputStats);
            map.put("outputStats", Iterables.getLast(db.getConcatStats(job), EncodingStats.none()));
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

        Spark.get("/browse", (req, res) -> {res.redirect("/browse/||CENTERCOURT|videotest"); return null;});

        Spark.get("/browse/:url", (req, res) -> {
            String url = req.params("url");
            String path = toPath(url);
            File file = new File(path);
            List<FrontendFile> directoryFiles = new ArrayList<>();
            if (file.isDirectory() && file.canRead()) {
                File[] files = file.listFiles();
                directoryFiles = Arrays.asList(files).stream()
                        .sorted((f1, f2) -> Boolean.compare(f2.isDirectory(), f1.isDirectory()))
                        .map(MovieServer::frontendFile)
                        .collect(Collectors.toList());
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
            String outputFile = Preconditions.checkNotNull(jobDetails.get("output").value(), "output file name cannot be null");
            String directory = Preconditions.checkNotNull(jobDetails.get("directory").value(), "Base directory cannot be null");
            String[] inputFiles = jobDetails.get("inputFiles").values();
            Integer startTrim = getTrim("startTrim", jobDetails);
            Integer endTrim = getTrim("endTrim", jobDetails);

            Preconditions.checkArgument(inputFiles.length > 1, "Must have at least two inputs");
            String jobId = UUID.randomUUID().toString();
            Job job = Job.create(jobId, name, directory, outputFile, Arrays.asList(inputFiles), startTrim, endTrim);
            db.saveJob(job);
            executor.execute(new FFMpegConcatWorker(db, runningProcesses, job.jobID, tempPath, ffmpegPath));

            res.redirect("/");
            return null;
        });

        System.out.println("Done setting up routes");

        Spark.exception(RuntimeException.class, new ExceptionHandler() {
            @Override
            public void handle(Exception exception, Request request, Response response) {
                response.status(500);
                response.body(exception.getMessage());
            }
        });
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
