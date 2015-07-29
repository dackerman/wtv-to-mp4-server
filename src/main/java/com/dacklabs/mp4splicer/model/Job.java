package com.dacklabs.mp4splicer.model;

import com.google.common.collect.Lists;

import java.util.Collections;
import java.util.List;

public class Job {
    public final String jobID;
    public final String name;
    public final String directory;
    public final FFMPEGFile outputPath;
    public final JobStatus status;
    public final List<FFMPEGFile> inputPaths;

    public static Job create(String jobId, String name, String directory, String outputPath, List<String> inputPaths) {
        if (!outputPath.endsWith(".mp4")) {
            outputPath += ".mp4";
        }
        return new Job(jobId, name, directory, FFMPEGFile.create(outputPath), JobStatus.CREATED, Lists.transform(inputPaths, FFMPEGFile::create));
    }

    public Job(String jobId, String name, String directory, FFMPEGFile outputPath, JobStatus status, List<FFMPEGFile> inputPaths) {
        this.jobID = jobId;
        this.name = name;
        this.directory = directory;
        this.outputPath = outputPath;
        this.status = status;
        this.inputPaths = Collections.unmodifiableList(inputPaths);
    }

    public Job updateInputStatus(FFMPEGFile inputFile) {
        return new Job(jobID, name, directory, outputPath, status, Lists.transform(inputPaths, i -> {
            if (inputFile.path.equals(i.path)) return inputFile;
            return i;
        }));
    }

    public Job updateOutputStatus(FFMPEGFile outputFile) {
        return new Job(jobID, name, directory, outputFile, status, inputPaths);
    }

    public Job encoding() {
        return new Job(jobID, name, directory, outputPath, JobStatus.ENCODING, inputPaths);
    }

    public Job concatenating() {
        return new Job(jobID, name, directory, outputPath, JobStatus.CONCATENATING, inputPaths);
    }

    public Job copyingOutput() {
        return new Job(jobID, name, directory, outputPath, JobStatus.COPYING_OUTPUT, inputPaths);
    }

    public Job done() {
        return new Job(jobID, name, directory, outputPath, JobStatus.DONE, inputPaths);
    }
}
