package com.dacklabs.mp4splicer;

import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Created by david on 7/25/2015.
 */
public class Database {

    private final DB db;

    public enum JobStatus {
        WAITING("Waiting to be picked up"),
        STARTED("Started"),
        CONVERTING_TO_TS("Converting to TS format"),
        CONCATENATING("Concatenating"),
        DONE("Completed");

        private final String name;

        JobStatus(String name) {
            this.name = name;
        }
    }

    public static class Job {
        public final String jobID;
        public final String name;
        public final String outputPath;
        public final Double progress;
        public final JobStatus status;
        public final List<String> inputPaths;

        public Job(String jobId, String name, String outputPath, List<String> inputPaths) {
            this(jobId, name, outputPath, 0.0, JobStatus.WAITING, inputPaths);
        }

        public Job(String jobId, String name, String outputPath, Double progress, JobStatus status, List<String> inputPaths) {
            this.jobID = jobId;
            this.name = name;
            this.outputPath = outputPath;
            this.progress = progress;
            this.status = status;
            this.inputPaths = Collections.unmodifiableList(inputPaths);
        }

        public Job start() {
            return new Job(jobID, name, outputPath, 1.0, JobStatus.STARTED, inputPaths);
        }

        public Job concatenating() {
            return new Job(jobID, name, outputPath, 60.0, JobStatus.CONCATENATING, inputPaths);
        }

        public Job done() {
            return new Job(jobID, name, outputPath, 100.0, JobStatus.DONE, inputPaths);
        }
    }

    public Database(String databasePath) {
        db = DBMaker.newFileDB(new File(databasePath)).make();
        db.createHashMap("jobs").valueSerializer(new JobSerializer()).makeOrGet();
    }

    public Job getJob(String jobID) {
        HTreeMap<String, Job> jobs = db.getHashMap("jobs");
        return jobs.get(jobID);
    }

    public Collection<Job> jobs() {
        HTreeMap<String, Job> jobs = db.getHashMap("jobs");
        return jobs.values();
    }

    public Job saveJob(Job job) {
        HTreeMap<String, Job> jobs = db.getHashMap("jobs");
        jobs.put(job.jobID, job);
        db.commit();
        return job;
    }

    public void appendLogs(String jobId, String log) throws IOException {
        BufferedOutputStream bos =
                new BufferedOutputStream(new FileOutputStream("logs/" + jobId, true));
        bos.write(log.getBytes());
    }

    public List<List<String>> getLogs(String jobId) {
        Job job = getJob(jobId);
        List<List<String>> logs = new ArrayList<>();
        try {
            for (int i = 0; i < job.inputPaths.size(); i++) {
                logs.add(Files.readAllLines(Paths.get("logs/convert-" + jobId + "-" + i + "-errors.txt")));
                logs.add(Files.readAllLines(Paths.get("logs/convert-" + jobId + "-" + i + "-output.txt")));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return logs;
    }

    private static class JobSerializer implements Serializable, Serializer<Job> {

        @Override
        public void serialize(DataOutput out, Job job) throws IOException {
            out.writeUTF(job.jobID);
            out.writeUTF(job.name);
            out.writeUTF(job.outputPath);
            out.writeDouble(job.progress);
            out.writeUTF(job.status.toString());
            out.writeInt(job.inputPaths.size());
            for (String inputPath : job.inputPaths) {
                out.writeUTF(inputPath);
            }
        }

        @Override
        public Job deserialize(DataInput in, int available) throws IOException {
            String jobID = in.readUTF();
            String name = in.readUTF();
            String outputPath = in.readUTF();
            Double progress = in.readDouble();
            JobStatus status = JobStatus.valueOf(in.readUTF());
            int numInputs = in.readInt();
            List<String> inputPaths = new ArrayList<>();
            for (int i = 0; i < numInputs; i++) {
                inputPaths.add(in.readUTF());
            }
            return new Job(jobID, name, outputPath, progress, status, inputPaths);
        }

        @Override
        public int fixedSize() {
            return -1;
        }
    }
}
