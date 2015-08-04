package com.dacklabs.mp4splicer.model;

import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Job {
    public final String jobID;
    public final String name;
    public final String directory;
    public final FFMPEGFile outputPath;
    public final Integer startTrimTimeSeconds;
    public final Integer endTrimTimeSeconds;
    public final JobStatus status;
    public final List<FFMPEGFile> inputPaths;
    public final LocalDateTime createDate;
    public final LocalDateTime endDate;
    public final boolean goFast;

    public static Job create(String jobId, String name, String directory, String outputPath, List<String> inputPaths,
                             Integer startTrimTimeSeconds, Integer endTrimTimeSeconds, boolean goFast) {
        if (!outputPath.endsWith(".mp4")) {
            outputPath += ".mp4";
        }
        LocalDateTime createDate = LocalDateTime.now();
        return new Job(jobId, createDate, null, name, directory, FFMPEGFile.create(outputPath), JobStatus.CREATED, Lists.transform(inputPaths, FFMPEGFile::create), startTrimTimeSeconds, endTrimTimeSeconds, goFast);
    }

    public Job(String jobId, LocalDateTime createDate, LocalDateTime endDate, String name, String directory, FFMPEGFile outputPath, JobStatus status, List<FFMPEGFile> inputPaths,
               Integer startTrimTimeSeconds, Integer endTrimTimeSeconds, boolean goFast) {
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

    public String formattedElapsedTime() {
        Duration elapsedTime = Duration.between(createDate, endDate == null ? LocalDateTime.now() : endDate);
        long days = elapsedTime.toDays();
        elapsedTime = elapsedTime.minusDays(days);
        long hours = elapsedTime.toHours();
        elapsedTime = elapsedTime.minusHours(hours);
        long minutes = elapsedTime.toMinutes();
        elapsedTime = elapsedTime.minusMinutes(minutes);
        long seconds = elapsedTime.getSeconds();

        List<String> units = new ArrayList<>();
        if (days > 0) {
            units.add(days + " days");
        }
        if (hours > 0) {
            units.add(hours + " hours");
        }
        if (minutes > 0) {
            units.add(minutes + " minutes");
        }
        if (seconds > 0) {
            units.add(seconds + " seconds");
        }
        return Joiner.on(", ").join(units);
    }

    public double percentComplete() {
        switch (status) {
            case CREATED:
                return 0;
            case ENCODING:
                double avgInputPercent = 0;
                for (FFMPEGFile inputPath : inputPaths) {
                    avgInputPercent += inputPath.percentComplete();
                }
                avgInputPercent /= inputPaths.size();
                return rangeBetween(avgInputPercent, 5, 70); // range: 5% - 70%
            case CONCATENATING:
                return rangeBetween(outputPath.percentComplete(), 70, 95); // range: 33% - 66
            case COPYING_OUTPUT:
                return 95;
            case DONE:
                return 100.0;
        }
        return -1;
    }

    public String concatErrorsLogFile() {
        return String.format("logs/job-%s-%s-concat-stderr.log", jobID, name);
    }

    public String concatOutputLogFile() {
        return String.format("logs/job-%s-%s-concat-stdout.log", jobID, name);
    }

    public String inputConversionErrorsLogFile(int index) {
        FFMPEGFile input = inputPaths.get(index);
        String inputName = Iterables.getLast(Lists.newArrayList(input.path.split("[\\\\/]")));
        return String.format("logs/job-%s-%s-%s-%s-convert-stderr.log", jobID, name, inputName, index);
    }

    public String inputConversionOutputLogFile(int index) {
        FFMPEGFile input = inputPaths.get(index);
        String inputName = Iterables.getLast(Lists.newArrayList(input.path.split("[\\\\/]")));
        return String.format("logs/job-%s-%s-%s-%s-convert-stdout.log", jobID, name, inputName, index);
    }

    private double rangeBetween(double value, int lowPercent, int highPercent) {
        int diff = highPercent - lowPercent;
        return value * (diff / 100.0) + lowPercent;
    }

    public Job updateInputStatus(int inputIndex, EncodingStatus encoding) {
        FFMPEGFile inputFile = inputPaths.get(inputIndex).transitionTo(encoding);
        return updateJob(endDate, outputPath, status, Lists.transform(inputPaths, i -> {
            if (inputFile.path.equals(i.path)) return inputFile;
            return i;
        }));
    }

    public Job updateOutputStatus(EncodingStatus newStatus) {
        return updateJob(endDate, outputPath.transitionTo(newStatus), status, inputPaths);
    }

    public Job encoding() {
        return updateJob(endDate, outputPath, JobStatus.ENCODING, inputPaths);
    }

    public Job concatenating() {
        return updateJob(endDate, outputPath, JobStatus.CONCATENATING, inputPaths);
    }

    public Job copyingOutput() {
        return updateJob(endDate, outputPath, JobStatus.COPYING_OUTPUT, inputPaths);
    }

    public Job done() {
        return updateJob(LocalDateTime.now(), outputPath, JobStatus.DONE, inputPaths);
    }

    public Job cancel() {
        return updateJob(endDate, outputPath, JobStatus.CANCELED, inputPaths);
    }

    private Job updateJob(LocalDateTime endDate, FFMPEGFile outputPath, JobStatus status, List<FFMPEGFile> inputPaths) {
        return new Job(jobID, createDate, endDate, name, directory, outputPath, status, inputPaths,
                       startTrimTimeSeconds, endTrimTimeSeconds, goFast);
    }
}
