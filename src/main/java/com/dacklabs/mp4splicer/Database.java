package com.dacklabs.mp4splicer;

import com.dacklabs.mp4splicer.model.EncodingStats;
import com.dacklabs.mp4splicer.model.Job;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jsr310.JSR310Module;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    public List<EncodingStats> getConcatStats(Job job) {
        return getStatsFromLog(Paths.get(job.concatErrorsLogFile()));
    }

    public List<EncodingStats> getInputStats(Job job, int i) {
        return getStatsFromLog(Paths.get(job.inputConversionErrorsLogFile(i)));
    }

    private List<EncodingStats> getStatsFromLog(Path logFile) {
        List<EncodingStats> stats = new ArrayList<>();
        Pattern statsPattern = Pattern.compile(
                ".*frame=\\s*(?<frame>\\d+) fps=\\s*(?<fps>[\\d.]+).*size=\\s*(?<size>\\d+kB) time=(?<time>.*) " +
                        "bitrate=(?<bitrate>[^ ]*).*");
        try {
            for (String line : Files.readAllLines(logFile, Charset.forName("UTF-8"))) {
                Matcher matcher = statsPattern.matcher(line);

                if (!matcher.matches()) continue;

                int frame = Integer.parseInt(matcher.group("frame"));
                double fps = Double.parseDouble(matcher.group("fps"));
                String sizeinKb = matcher.group("size");
                String estimatedTimeLeft = matcher.group("time");
                String bitrate = matcher.group("bitrate");
                stats.add(new EncodingStats(frame, fps, sizeinKb, estimatedTimeLeft, bitrate, 0));
            }
        } catch(NoSuchFileException nsfe){
            // do nothing, this is normal
        } catch (IOException e) {
            e.printStackTrace();
        }
        return stats;
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
