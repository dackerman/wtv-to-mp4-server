package com.dacklabs.mp4splicer.workers;

import com.dacklabs.mp4splicer.Database;
import com.dacklabs.mp4splicer.model.EncodingStats;
import com.dacklabs.mp4splicer.model.Job;
import com.dacklabs.mp4splicer.model.StatsMatcher;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

public class FFMpegLogWatcher extends Thread {

    private final Job job;
    private final Database db;
    private final InputStream errorStream;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private static final ObjectMapper om = new ObjectMapper();

    public FFMpegLogWatcher(Job job, Database db, InputStream errorStream) {
        this.job = job;
        this.db = db;
        this.errorStream = errorStream;
    }

    @Override
    public void run() {
        try {
            Scanner scanner = new Scanner(errorStream);

            OpenOption[] openOptions =
                    {StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE};
            BufferedWriter stats = Files.newBufferedWriter(Paths.get(job.jobStatsFile()), Charsets.UTF_8, openOptions);
            BufferedWriter stdErr = Files.newBufferedWriter(Paths.get(job.jobStdErrFile()), Charsets.UTF_8, openOptions);
            while (running.get() && scanner.hasNextLine()) {
                String line = scanner.nextLine();
                stdErr.write(line);
                stdErr.newLine();
                Optional<EncodingStats> maybeStats = StatsMatcher.match(line);
                if (maybeStats.isPresent()) {
                    stats.write(om.writeValueAsString(maybeStats.get()));
                    stats.newLine();
                }
                stats.flush();
            }
            scanner.close();
            stats.close();
            stdErr.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void kill() {
        running.set(false);
    }
}
