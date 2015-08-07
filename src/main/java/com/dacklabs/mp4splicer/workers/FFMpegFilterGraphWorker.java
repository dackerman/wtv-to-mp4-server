package com.dacklabs.mp4splicer.workers;

import com.dacklabs.mp4splicer.Database;
import com.dacklabs.mp4splicer.ffmpeg.InputFileStats;
import com.dacklabs.mp4splicer.model.EncodingStatus;
import com.dacklabs.mp4splicer.model.InputFile;
import com.dacklabs.mp4splicer.model.Job;
import com.google.common.base.Joiner;
import com.google.common.collect.ListMultimap;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class FFMpegFilterGraphWorker implements Runnable {

    private final Database db;
    private final String jobId;
    private final String tempLocation;
    private final ListMultimap<String, Process> runningProcesses;
    private final String ffmpeg;

    public FFMpegFilterGraphWorker(Database db, ListMultimap<String, Process> runningProcesses, String jobId,
                            String tempLocation, String ffmpeg) {
        this.db = db;
        this.jobId = jobId;
        this.tempLocation = tempLocation;
        this.runningProcesses = runningProcesses;
        this.ffmpeg = ffmpeg;
    }

    @Override
    public void run() {
        try {
            Job job = db.getJob(jobId);

            for (InputFile inputFile : job.inputPaths) {
                job = job.updateInput(inputFile.withProbedStats(InputFileStats.probeStats(inputFile.path)));
            }
            db.saveJob(job);

            List<String> command = generateFFMpegCommand(job);

            System.out.println("Executing: " + Joiner.on(" ").join(command));

            job = db.saveJob(job.updateOutputStatus(EncodingStatus.ENCODING).concatenating());
            Process concatProcess = new ProcessBuilder().redirectError(new File(job.concatErrorsLogFile()))
                                                        .redirectOutput(new File(job.concatOutputLogFile()))
                                                        .command(command).start();
            runningProcesses.put(job.jobID, concatProcess);
            int concatReturnValue = concatProcess.waitFor();
            if (concatReturnValue != 0) {
                throw new RuntimeException("concat failed with exit code " + concatReturnValue);
            }
            db.saveJob(job.updateOutputStatus(EncodingStatus.DONE).done());
            System.out.println("Done.");
        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
        }
    }

    private List<String> generateFFMpegCommand(Job job) {
        List<String> command = new ArrayList<>();
        command.add(ffmpeg);
        command.add("-y");
        for (InputFile inputPath : job.inputPaths) {
            command.add("-i");
            command.add("\"" + inputPath.path + "\"");
        }
        command.add("-filter_complex");
        command.add("\"[0:1] [0:0] [1:1] [1:0] concat=n=2:v=1:a=1 [v] [a]\"");
        command.add("-map");
        command.add("\"[v]\"");
        command.add("-map");
        command.add("\"[a]\"");
        command.add("-b:v");
        command.add("10000k");

        if (job.startTrimTimeSeconds != null) {
            command.add("-ss");
            command.add(job.startTrimTimeSeconds.toString());
        }
        if (job.endTrimTimeSeconds != null) {
            command.add("-sseof");
            command.add("-" + job.endTrimTimeSeconds);
        }
        Path outputFullPath = Paths.get(job.directory, job.outputPath.path);
        command.add("\"" + outputFullPath + "\"");
        return command;
    }
}
