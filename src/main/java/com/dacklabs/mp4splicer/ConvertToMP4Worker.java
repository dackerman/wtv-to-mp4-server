package com.dacklabs.mp4splicer;

import com.dacklabs.mp4splicer.model.FFMPEGFile;
import com.dacklabs.mp4splicer.model.Job;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class ConvertToMP4Worker implements Runnable {

    private final Database db;
    private final String jobId;
    private final String tempLocation;

    ConvertToMP4Worker(Database db, String jobId, String tempLocation) {
        this.db = db;
        this.jobId = jobId;
        this.tempLocation = tempLocation;
    }

    @Override
    public void run() {
        try {
            Job job = db.getJob(jobId);
            job = db.saveJob(job.encoding());
            List<Process> conversionProcesses = new ArrayList<>();
            List<String> tmpPaths = new ArrayList<>();
            String baseDir = null;
            for (int inputIndex = 0; inputIndex < job.inputPaths.size(); inputIndex++) {
                FFMPEGFile input = job.inputPaths.get(inputIndex);
                File inputFile = new File(input.path);
                baseDir = inputFile.getParent();
                String tmpPath = tempLocation + "/" + inputFile.getName() + ".tmp.mp4";
                tmpPaths.add(tmpPath);
                List<String> command = Lists.newArrayList("ffmpeg", "-y", "-i", input.path, "-vcodec", "copy",
                                                "-acodec", "copy", "-target", "ntsc-dvd", tmpPath);
                System.out.println("Executing: " + Joiner.on(" ").join(command));

                conversionProcesses.add(new ProcessBuilder().redirectError(
                                                new File("logs/convert-" + jobId + "-" + inputIndex + "-errors.txt"))
                                                            .redirectOutput(new File(
                                                                    "logs/convert-" + jobId + "-" + inputIndex +
                                                                            "-output.txt")).command(command).start());
            }
            for (Process conversionProcess : conversionProcesses) {
                int returnValue = conversionProcess.waitFor();
                if (returnValue != 0) {
                    throw new RuntimeException("conversion process failed with return value " + returnValue);
                }
            }
            job = db.saveJob(job.concatenating());
            FFMPEGFile output = job.outputPath;
            List<String> command = new ArrayList<>();
            command.add("ffmpeg");
            command.add("-y");
            command.add("-i");
            command.add("\"concat:" + Joiner.on("|").join(tmpPaths) + "\"");
            command.add("-c");
            command.add("copy");
            Path tempOutput = Paths.get(tempLocation, output.path);
            command.add("\"" + tempOutput + "\"");

            System.out.println("Executing: " + Joiner.on(" ").join(command));

            Process concatProcess = new ProcessBuilder()
                    .redirectError(new File("logs/concat-" + jobId + "-errors.txt"))
                    .redirectOutput(new File("logs/concat-" + jobId + "-output.txt"))
                    .command(command)
                    .start();
            int concatReturnValue = concatProcess.waitFor();
            if (concatReturnValue != 0) {
                throw new RuntimeException("concat failed with exit code " + concatReturnValue);
            }
            System.out.println("Copying output to original directory " + baseDir);
            Files.copy(tempOutput, Paths.get(baseDir, output.path));
            db.saveJob(job.done());
            System.out.println("Done.");
        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
        }
    }
}
