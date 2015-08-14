package com.dacklabs.mp4splicer.workers;

import com.dacklabs.mp4splicer.Database;
import com.dacklabs.mp4splicer.ffmpeg.InputFileStats;
import com.dacklabs.mp4splicer.model.EncodingStatus;
import com.dacklabs.mp4splicer.model.InputFile;
import com.dacklabs.mp4splicer.model.Job;
import com.google.common.base.Joiner;
import com.google.common.collect.ListMultimap;

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
            System.out.println("Running job " + job.name + " (" + job.jobID + ")");
            job = db.saveJob(job.resetTimer());

            for (InputFile inputFile : job.inputPaths) {
                job = job.updateInput(inputFile.withProbedStats(InputFileStats.probeStats(ffmpeg, inputFile.path)));
            }
            db.saveJob(job);

            List<String> command = generateFFMpegCommand(job);

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

    private List<String> generateFFMpegCommand(Job job) {
        List<String> command = new ArrayList<>();
        command.add(ffmpeg);
        command.add("-y");

        if (job.startTrimTimeSeconds != null) {
            command.add("-ss");
            command.add(job.startTrimTimeSeconds.toString());
        }
        for (int i = 0; i < job.inputPaths.size(); i++) {
            boolean isLastInput = i == job.inputPaths.size() - 1;
            if (isLastInput && job.endTrimTimeSeconds != null) {
                command.add("-t");
                command.add(job.endTrimTimeSeconds + "");
            }
            command.add("-i");
            command.add("\"" + job.inputPaths.get(i).path + "\"");
        }
        if (job.inputPaths.size() > 1) {
            addFilterGraphConcat(job, command);
        }
        command.add("-b:v");
        int maxBitrate = job.inputPaths.stream().map(i -> i.stats.bitrate).max(Double::compare).orElse(10000);
        command.add(Math.min(maxBitrate, 15000) + "k");

        Path outputFullPath = Paths.get(job.directory, job.outputPath.path);
        command.add("\"" + outputFullPath + "\"");
        return command;
    }

    private void addFilterGraphConcat(Job job, List<String> command) {
        command.add("-filter_complex");
        StringBuilder filterGraph = new StringBuilder("\"");
        for (int i=0; i < job.inputPaths.size(); i++) {
            InputFile inputFile = job.inputPaths.get(i);
            filterGraph.append("[").append(i).append(":");
            filterGraph.append(inputFile.stats.videoStreams.get(0).streamNumber);
            filterGraph.append("] ");
            filterGraph.append("[").append(i).append(":");
            filterGraph.append(inputFile.stats.audioStreams.get(0).streamNumber);
            filterGraph.append("] ");
        }
        filterGraph.append("concat=n=").append(job.inputPaths.size());
        filterGraph.append(":v=1:a=1 [v] [a]\"");
        command.add(filterGraph.toString());
        command.add("-map");
        command.add("\"[v]\"");
        command.add("-map");
        command.add("\"[a]\"");
        command.add("-c:v");
        command.add("libx264");
    }
}
