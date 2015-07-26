package com.dacklabs.mp4splicer;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import spark.*;
import spark.template.jade.JadeTemplateEngine;

import java.io.File;
import java.util.*;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class MovieServer {
    public static void main(String[] argsArray) {
        Iterator<String> args = Lists.newArrayList(argsArray).iterator();
        boolean debug = false;
        File tempDir = null;
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
            }
        }
        Preconditions.checkNotNull(tempDir, "Specify a location to put intermediate files with -tmpDir");
        String tempPath = tempDir.getAbsolutePath();
        TemplateEngine templateEngine = new JadeTemplateEngine();
        if (debug) {
            System.out.println("Debugging");
            templateEngine = new ExternalJadeTemplateEngine();
        }

        ThreadPoolExecutor executor =
                new ThreadPoolExecutor(1, 5, 60, TimeUnit.SECONDS, new LinkedBlockingDeque<>());

        Database db = new Database("job-database");

        for (Database.Job job : db.jobs()) {
            if (!job.status.equals(Database.JobStatus.DONE)) {
                System.out.println("Restarting incomplete job " + job.jobID);
                executor.execute(new ConvertToMP4Worker(db, job.jobID, tempDir.getAbsolutePath()));
            }
        }

        Spark.externalStaticFileLocation("R:/Projects/mp4-splicer/src/main/resources");
        Spark.get("/", (req, res) -> {
            Map<String, Object> map = new HashMap<>();
            map.put("jobs", db.jobs());
            return new ModelAndView(map, "main");
        }, templateEngine);

        Spark.get("/jobs/:jobId", (req, res) -> {
            String jobId = req.params("jobId");
            Database.Job job = db.getJob(jobId);
            Map<String, Object> map = new HashMap<>();
            map.put("job", job);
            map.put("jobLogs", db.getLogs(jobId));
            return new ModelAndView(map, "job");
        }, templateEngine);

        Spark.get("/browse", (req, res) -> {res.redirect("/browse/||CENTERCOURT|videotest"); return null;});

        Spark.get("/browse/:url", (req, res) -> {
            String url = req.params("url");
            String path = toPath(url);
            File file = new File(path);
            List<Map<String, Object>> directoryFiles = new ArrayList<>();
            if (file.isDirectory() && file.canRead()) {
                File[] files = file.listFiles();
                directoryFiles = Arrays.asList(files).stream()
                        .sorted((f1, f2) -> Boolean.compare(f2.isDirectory(), f1.isDirectory()))
                        .filter(f -> f.isDirectory() || f.getName().endsWith(".wtv"))
                        .map(MovieServer::frontendFile)
                        .collect(Collectors.toList());
                File parent = file.getParentFile();
                if (parent != null && parent.exists()) {
                    Map<String, Object> parentObj = frontendFile(parent);
                    parentObj.put("name", "..");
                    directoryFiles.add(0, parentObj);
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
            String outputFile = Preconditions.checkNotNull(jobDetails.get("output").value(),
                                                           "output file name cannot be null");
            String[] inputFiles = jobDetails.get("inputFiles").values();
            Preconditions.checkArgument(inputFiles.length > 1, "Must have at least two inputs");
            String jobId = UUID.randomUUID().toString();
            Database.Job job = new Database.Job(jobId, name, outputFile, Arrays.asList(inputFiles));
            db.saveJob(job);
            executor.execute(new ConvertToMP4Worker(db, job.jobID, tempPath));

            res.redirect("/");
            return null;
        });

        Spark.exception(RuntimeException.class, new ExceptionHandler() {
            @Override
            public void handle(Exception exception, Request request, Response response) {
                response.status(500);
                response.body(exception.getMessage());
            }
        });
    }

    private static Map<String, Object> frontendFile(File f) {
        Map<String, Object> folder = new HashMap<>();
        folder.put("url", fromPath(f.getAbsolutePath()));
        folder.put("path", f.getAbsolutePath());
        folder.put("isDirectory", f.isDirectory());
        folder.put("name", f.getName());
        return folder;
    }

    private static String toPath(String url) {
        return Joiner.on("/").join(url.split("\\|"));
    }

    private static String fromPath(String path) {
        return "/browse/" + Joiner.on("|").join(path.split("\\\\"));
    }

    private static class BrowseFolder {
        public final String url;
        public final String name;

        private BrowseFolder(String absolutePath, String name) {
            this.url = fromPath(absolutePath);
            this.name = name;
        }
    }
}
