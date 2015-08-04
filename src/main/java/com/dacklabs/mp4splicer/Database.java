package com.dacklabs.mp4splicer;

import com.dacklabs.mp4splicer.model.*;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

        private static final int VERSION = 1;

        @Override
        public void serialize(DataOutput out, Job job) throws IOException {
            out.writeInt(VERSION);
            out.writeUTF(job.jobID);
            out.writeUTF(job.name);
            out.writeInt(job.startTrimTimeSeconds != null ? job.startTrimTimeSeconds : Integer.MIN_VALUE);
            out.writeInt(job.endTrimTimeSeconds != null ? job.endTrimTimeSeconds : Integer.MIN_VALUE);
            out.writeUTF(serializeDate(job.createDate));
            out.writeBoolean(job.endDate != null);
            if (job.endDate != null) {
                out.writeUTF(serializeDate(job.endDate));
            }
            out.writeUTF(job.directory);
            writeFFMPEGFile(out, job.outputPath);
            out.writeUTF(job.status.name());
            out.writeInt(job.inputPaths.size());
            for (FFMPEGFile inputPath : job.inputPaths) {
                writeFFMPEGFile(out, inputPath);
            }
            out.writeBoolean(job.goFast);
        }

        @Override
        public Job deserialize(DataInput in, int available) throws IOException {
            int version = in.readInt();
            String jobID = in.readUTF();
            String name = in.readUTF();
            int startTrim = in.readInt();
            int endTrim = in.readInt();
            LocalDateTime createDate = parseDate(in.readUTF());
            LocalDateTime endDate = null;
            boolean hasEndDate = in.readBoolean();
            if (hasEndDate) {
                endDate = parseDate(in.readUTF());
            }
            String directory = in.readUTF();
            FFMPEGFile outputPath = readFFMPEGFile(in);
            JobStatus status = JobStatus.valueOf(in.readUTF());
            int numInputs = in.readInt();
            List<FFMPEGFile> inputPaths = new ArrayList<>();
            for (int i = 0; i < numInputs; i++) {
                inputPaths.add(readFFMPEGFile(in));
            }
            boolean goFast = in.readBoolean();
            return new Job(jobID, createDate, endDate, name, directory, outputPath, status, inputPaths,
                           startTrim != Integer.MIN_VALUE ? startTrim : null,
                           endTrim != Integer.MIN_VALUE ? endTrim : null, goFast);
        }

        private String serializeDate(LocalDateTime date) {
            return date.format(DateTimeFormatter.ISO_DATE_TIME);
        }

        private LocalDateTime parseDate(String input) {
            return LocalDateTime.from(DateTimeFormatter.ISO_DATE_TIME.parse(input));
        }

        private void writeFFMPEGFile(DataOutput out, FFMPEGFile f) throws IOException {
            out.writeUTF(f.path);
            out.writeUTF(f.encodingStatus.name());
        }

        private FFMPEGFile readFFMPEGFile(DataInput in) throws IOException {
            String path = in.readUTF();
            EncodingStatus encodingStatus = EncodingStatus.valueOf(in.readUTF());

            return new FFMPEGFile(path, encodingStatus);
        }

        @Override
        public int fixedSize() {
            return -1;
        }
    }
}
