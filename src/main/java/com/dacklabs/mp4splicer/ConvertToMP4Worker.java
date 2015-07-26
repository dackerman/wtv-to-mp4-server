package com.dacklabs.mp4splicer;

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
            Database.Job job = db.getJob(jobId);
            job = db.saveJob(job.start());
            List<Process> conversionProcesses = new ArrayList<>();
            List<String> tmpPaths = new ArrayList<>();
            String baseDir = null;
            for (int inputIndex = 0; inputIndex < job.inputPaths.size(); inputIndex++) {
                String inputPath = job.inputPaths.get(inputIndex);
                File inputFile = new File(inputPath);
                baseDir = inputFile.getParent();
                String tmpPath = tempLocation + "/" + inputFile.getName() + ".tmp.mp4";
                tmpPaths.add(tmpPath);
                List<String> command = Lists.newArrayList("ffmpeg", "-y", "-i", inputPath, "-vcodec", "copy",
                                                "-acodec", "copy", "-target", "ntsc-dvd", tmpPath);
                System.out.println("Executing: " + Joiner.on(" ").join(command));

                conversionProcesses.add(
                        new ProcessBuilder()
                                .redirectError(new File("logs/convert-" + jobId + "-" + inputIndex + "-errors.txt"))
                                .redirectOutput(new File("logs/convert-" + jobId + "-" + inputIndex + "-output.txt"))
                                .command(command)
                                .start());
            }
            for (Process conversionProcess : conversionProcesses) {
                int returnValue = conversionProcess.waitFor();
                if (returnValue != 0) {
                    throw new RuntimeException("conversion process failed with return value " + returnValue);
                }
            }
            job = db.saveJob(job.concatenating());
            String outputPath = job.outputPath;
            if (!outputPath.endsWith(".mp4")) {
                outputPath += ".mp4";
            }
            List<String> command = new ArrayList<>();
            command.add("ffmpeg");
//            command.add("-f");
//            command.add("concat");
//            for (String tmpPath : tmpPaths) {
//                command.add("-i");
//                command.add("'" + tmpPath + "'");
//            }
//            command.add("-c:v");
            command.add("-y");
            command.add("-i");
            command.add("\"concat:" + Joiner.on("|").join(tmpPaths) + "\"");
            command.add("-c");
            command.add("copy");
            Path tempOutput = Paths.get(tempLocation, outputPath);
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
            Files.copy(tempOutput, Paths.get(baseDir, outputPath));
            db.saveJob(job.done());
            System.out.println("Done.");
        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
        }
    }
}
