package com.dacklabs.mp4splicer.model;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StatsMatcher {
    private static final Pattern statsPattern = Pattern.compile(
            ".*frame=\\s*(?<frame>\\d+) fps=\\s*(?<fps>[\\d.]+).*size=\\s*(?<size>\\d+kB) time=(?<time>.*) " +
                    "bitrate=(?<bitrate>[^ ]*).*");

    public static Optional<EncodingStats> match(String line) {
        Matcher matcher = statsPattern.matcher(line);

        if (!matcher.matches()) return Optional.empty();

        int frame = Integer.parseInt(matcher.group("frame"));
        double fps = Double.parseDouble(matcher.group("fps"));
        String sizeinKb = matcher.group("size");
        String estimatedTimeLeft = matcher.group("time");
        String bitrate = matcher.group("bitrate");
        return Optional.of(new EncodingStats(frame, fps, sizeinKb, estimatedTimeLeft, bitrate, 0));
    }
}
