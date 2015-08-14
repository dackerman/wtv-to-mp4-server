package com.dacklabs.mp4splicer.workers;

import com.dacklabs.mp4splicer.Database;
import com.dacklabs.mp4splicer.ffmpeg.InputFileStats;
import com.dacklabs.mp4splicer.model.EncodingStatus;
import com.dacklabs.mp4splicer.model.InputFile;
import com.dacklabs.mp4splicer.model.Job;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.ListMultimap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class FFMpegConcatWorker implements Runnable {

    private final Database db;
    private final String jobId;
    private final String tempLocation;
    private final ListMultimap<String, Process> runningProcesses;
    private final String ffmpeg;

    public FFMpegConcatWorker(Database db, ListMultimap<String, Process> runningProcesses, String jobId,
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
            System.out.println("Running job " + job.name + " (" + job.jobID + ")");
            job = db.saveJob(job.resetTimer());

            for (InputFile inputFile : job.inputPaths) {
                job = job.updateInput(inputFile.withProbedStats(InputFileStats.probeStats(ffmpeg, inputFile.path)));
            }
            db.saveJob(job);

            Path inputFilesConfigPath = writeFFMpegConfigFile(job);

            List<String> command = generateFFMpegCommand(job, inputFilesConfigPath);

            System.out.println("Executing: " + Joiner.on(" ").join(command));

            job = db.saveJob(job.updateOutputStatus(EncodingStatus.ENCODING).encoding());
            Process concatProcess = new ProcessBuilder().command(command).start();
            FFMpegLogWatcher logWatcher = new FFMpegLogWatcher(job, concatProcess.getErrorStream());
            logWatcher.start();
            runningProcesses.put(job.jobID, concatProcess);
            int concatReturnValue = concatProcess.waitFor();
            if (concatReturnValue != 0) {
                throw new RuntimeException("concat failed with exit code " + concatReturnValue);
            }
            db.saveJob(job.updateOutputStatus(EncodingStatus.DONE).done());
            System.out.println("Done.");
            logWatcher.kill();
        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
        }
    }

    private Path writeFFMpegConfigFile(Job job) throws IOException {
        List<String> configLines = new ArrayList<>();
        for (InputFile inputPath : job.inputPaths) {
            configLines.add("file " + inputPath.path.replace("\\", "/").replace(" ", "\\ "));
        }

        Path inputFilesConfig = Paths.get(tempLocation, job.jobID + "-config.txt");
        Files.write(inputFilesConfig, configLines, Charsets.UTF_8);
        return inputFilesConfig;
    }

    private List<String> generateFFMpegCommand(Job job, Path inputFilesConfig) {
        List<String> command = new ArrayList<>();
        command.add(ffmpeg);
        command.add("-y");
        command.add("-f");
        command.add("concat");
        command.add("-i");
        command.add(inputFilesConfig.toString());
        command.add("-c");
        command.add("copy");
        if (job.startTrimTimeSeconds != null) {
            command.add("-ss");
            command.add(job.startTrimTimeSeconds.toString());
        }
        if (job.endTrimTimeSeconds != null) {
            command.add("-t");
            command.add(job.endTrimTimeSeconds + "");
        }
        Path outputFullPath = Paths.get(job.directory, job.outputPath.path);
        command.add("\"" + outputFullPath + "\"");
        return command;
    }
}
