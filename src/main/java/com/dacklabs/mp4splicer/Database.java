package com.dacklabs.mp4splicer;

import com.dacklabs.mp4splicer.model.EncodingStats;
import com.dacklabs.mp4splicer.model.Job;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jsr310.JSR310Module;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Database {

    private final DB db;

    public Database(String databasePath) {
        db = DBMaker.newFileDB(new File(databasePath)).make();
        db.createHashMap("jobs").valueSerializer(new JobSerializer()).makeOrGet();
    }

    public Job getJob(String jobID) {
        HTreeMap<String, Job> jobs = db.getHashMap("jobs");
        return jobs.get(jobID);
    }

    public List<Job> jobs() {
        HTreeMap<String, Job> jobs = db.getHashMap("jobs");
        return Lists.newArrayList(jobs.values());
    }

    public Job saveJob(Job job) {
        HTreeMap<String, Job> jobs = db.getHashMap("jobs");
        jobs.put(job.jobID, job);
        db.commit();
        return job;
    }

    public void deleteJob(String jobID) {
        db.getHashMap("jobs").remove(jobID);
        db.commit();
    }

    public List<EncodingStats> getJobStats(Job job) {
        return getStatsFromLog(Paths.get(job.jobStatsFile()));
    }

    private List<EncodingStats> getStatsFromLog(Path logFile) {
        ObjectMapper om = new ObjectMapper();
        try {
            return Files.lines(logFile, Charsets.UTF_8).map(line -> {
                try {
                    return om.readValue(line, EncodingStats.class);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return null;
            }).collect(Collectors.toList());
        } catch (NoSuchFileException nsfe) {
            // do nothing, this is normal
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    private static class JobSerializer implements Serializable, Serializer<Job> {

        private static final ObjectMapper om = new ObjectMapper();
        static {
            om.registerModule(new JSR310Module());
            om.registerModule(new GuavaModule());
        }

        @Override
        public void serialize(DataOutput out, Job job) throws IOException {
            out.writeUTF(om.writeValueAsString(job));
        }

        @Override
        public Job deserialize(DataInput in, int available) throws IOException {
            return om.readValue(in.readUTF(), Job.class);
        }

        @Override
        public int fixedSize() {
            return -1;
        }
    }
}
