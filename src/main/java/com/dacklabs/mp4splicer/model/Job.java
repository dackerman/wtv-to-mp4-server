package com.dacklabs.mp4splicer.model;

import com.dacklabs.mp4splicer.ffmpeg.InputFileStats;
import com.dacklabs.mp4splicer.ffmpeg.VideoStream;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class Job {
    public final String jobID;
    public final String name;
    public final String directory;
    public final FFMPEGFile outputPath;
    public final Integer startTrimTimeSeconds;
    public final Integer endTrimTimeSeconds;
    public final JobStatus status;
    public final List<InputFile> inputPaths;
    public final LocalDateTime createDate;
    public final LocalDateTime endDate;
    public final boolean goFast;

    public static Job create(String jobId, String name, String directory, String outputPath, List<String> inputPaths,
                             Integer startTrimTimeSeconds, Integer endTrimTimeSeconds, boolean goFast) {
        if (!outputPath.endsWith(".mp4")) {
            outputPath += ".mp4";
        }
        LocalDateTime createDate = LocalDateTime.now();
        return new Job(jobId, createDate, null, name, directory, FFMPEGFile.create(outputPath), JobStatus.CREATED, Lists.transform(inputPaths, InputFile::create), startTrimTimeSeconds, endTrimTimeSeconds, goFast);
    }

    @JsonCreator
    public Job(@JsonProperty("jobID") String jobId,
               @JsonProperty("createDate") LocalDateTime createDate,
               @JsonProperty("endDate") LocalDateTime endDate,
               @JsonProperty("name") String name,
               @JsonProperty("directory") String directory,
               @JsonProperty("outputPath") FFMPEGFile outputPath,
               @JsonProperty("status") JobStatus status,
               @JsonProperty("inputPaths") List<InputFile> inputPaths,
               @JsonProperty("startTrimTimeSeconds") Integer startTrimTimeSeconds,
               @JsonProperty("endTrimTimeSeconds") Integer endTrimTimeSeconds,
               @JsonProperty("goFast") boolean goFast) {
        this.jobID = jobId;
        this.name = name;
        this.directory = directory;
        this.outputPath = outputPath;
        this.status = status;
        this.inputPaths = Collections.unmodifiableList(inputPaths);
        this.createDate = createDate;
        this.endDate = endDate;
        this.startTrimTimeSeconds = startTrimTimeSeconds;
        this.endTrimTimeSeconds = endTrimTimeSeconds;
        this.goFast = goFast;
    }

    public String formatStartTrim() {
        if (startTrimTimeSeconds != null)
            return formatDuration(Duration.of(startTrimTimeSeconds, ChronoUnit.SECONDS));
        return "N/A";
    }

    public String formatEndTrim() {
        if (endTrimTimeSeconds != null)
            return formatDuration(Duration.of(endTrimTimeSeconds, ChronoUnit.SECONDS));
        return "N/A";
    }

    public String formattedStartTime() {
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).format(createDate);
    }

    public String formattedElapsedTime() {
        Duration elapsedTime = Duration.between(createDate, endDate == null ? LocalDateTime.now() : endDate);
        return formatDuration(elapsedTime);
    }

    public static String formatDuration(Duration elapsedTime) {
        long days = elapsedTime.toDays();
        elapsedTime = elapsedTime.minusDays(days);
        long hours = elapsedTime.toHours();
        elapsedTime = elapsedTime.minusHours(hours);
        long minutes = elapsedTime.toMinutes();
        elapsedTime = elapsedTime.minusMinutes(minutes);
        long seconds = elapsedTime.getSeconds();

        List<String> units = new ArrayList<>();
        if (days > 0) {
            units.add(days + " day" + (days > 1 ? "s" : ""));
        }
        if (hours > 0) {
            units.add(hours + " hour" + (hours > 1 ? "s" : ""));
        }
        if (minutes > 0) {
            units.add(minutes + " minute" + (minutes > 1 ? "s" : ""));
        }
        units.add(seconds + " second" + (seconds == 1 ? "" : "s"));
        return Joiner.on(", ").join(units);
    }

    public double percentComplete(EncodingStats currentOutputStats) {
        if (status.equals(JobStatus.DONE)) {
            return 100.0;
        }
        long totalFrames = calculateTotalFrames();

        int frame = currentOutputStats.frame;
        totalFrames = Math.max(totalFrames, 1); // avoid div by zero
        return Math.max(Math.min(new BigDecimal(frame).setScale(5, BigDecimal.ROUND_UNNECESSARY)
                                                      .divide(new BigDecimal(totalFrames), BigDecimal.ROUND_HALF_UP)
                                                      .movePointRight(2).doubleValue(), 100.0), 0.0);
    }

    private long calculateTotalFrames() {
        long totalFrames = 0;
        for (int i = 0; i < inputPaths.size(); i++) {
            InputFile file = inputPaths.get(i);
            InputFileStats stats = file.stats;
            ImmutableList<VideoStream> videoStreams = stats.videoStreams;
            if (i == inputPaths.size() - 1 && !videoStreams.isEmpty()) {
                totalFrames += endTrimTimeSeconds != null ? videoStreams.get(0).fps.multiply(new BigDecimal(endTrimTimeSeconds)).longValue() : stats.totalFrames();
            } else {
                totalFrames += stats.totalFrames();
            }
            if (i == 0 && videoStreams.size() > 1 && startTrimTimeSeconds != null) {
                totalFrames -= videoStreams.get(0).fps.multiply(new BigDecimal(startTrimTimeSeconds)).longValue();
            }
        }
        return totalFrames;
    }

    public String jobStatsFile() {
        return String.format("logs/job-%s-%s-stats.log", jobID, name);
    }

    public String jobStdErrFile() {
        return String.format("logs/job-%s-%s-stderr.log", jobID, name);
    }

    public Job updateInput(InputFile newInput) {
        return updateJob(endDate, outputPath, status,
                         Lists.transform(inputPaths, i -> newInput.path.equals(i.path) ? newInput : i));
    }

    public Job updateOutputStatus(EncodingStatus newStatus) {
        return updateJob(endDate, outputPath.transitionTo(newStatus), status, inputPaths);
    }

    public Job encoding() {
        return updateJob(endDate, outputPath, JobStatus.ENCODING, inputPaths);
    }

    public Job done() {
        return updateJob(LocalDateTime.now(), outputPath, JobStatus.DONE, inputPaths);
    }

    public Job cancel() {
        return updateJob(endDate, outputPath, JobStatus.CANCELED, inputPaths);
    }

    public Job resetTimer() {
        return new Job(jobID, LocalDateTime.now(), endDate, name, directory, outputPath, status, inputPaths,
                       startTrimTimeSeconds, endTrimTimeSeconds, goFast);
    }

    private Job updateJob(LocalDateTime endDate, FFMPEGFile outputPath, JobStatus status, List<InputFile> inputPaths) {
        return new Job(jobID, createDate, endDate, name, directory, outputPath, status, inputPaths,
                       startTrimTimeSeconds, endTrimTimeSeconds, goFast);
    }

    private static int statusSort(Job job) {
        int sort = 0;
        if (job.status.equals(JobStatus.CANCELED)) {
            sort -= 20;
        }
        if (job.status.equals(JobStatus.DONE)) {
            sort -= 10;
        }
        return sort;
    }

    public static Comparator<Job> COMPARATOR = (job1, job2) -> {
        int diff = statusSort(job2) - statusSort(job1);
        if (diff != 0) {
            return diff;
        } else {
            return job2.createDate.compareTo(job1.createDate);
        }
    };
}
