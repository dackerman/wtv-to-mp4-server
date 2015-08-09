package com.dacklabs.mp4splicer.ffmpeg;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InputFileStats {

    public final int inputNumber;
    public final String fileName;
    public final ImmutableMap<String, String> metadata;
    public final ImmutableList<AudioStream> audioStreams;
    public final ImmutableList<VideoStream> videoStreams;
    public final Duration duration;
    public final int bitrate;

    @JsonCreator
    public InputFileStats(@JsonProperty("inputNumber") int inputNumber, @JsonProperty("fileName") String fileName,
                          @JsonProperty("metadata") ImmutableMap<String, String> metadata,
                          @JsonProperty("audioStreams") ImmutableList<AudioStream> audioStreams,
                          @JsonProperty("videoStreams") ImmutableList<VideoStream> videoStreams,
                          @JsonProperty("duration") Duration duration,
                          @JsonProperty("bitrate") int bitrate) {
        this.inputNumber = inputNumber;
        this.fileName = fileName;
        this.metadata = metadata;
        this.audioStreams = audioStreams;
        this.videoStreams = videoStreams;
        this.duration = duration;
        this.bitrate = bitrate;
    }

    public static InputFileStats none() {
        return new InputFileStats(-1, "", ImmutableMap.<String, String>of(), ImmutableList.<AudioStream>of(), ImmutableList.<VideoStream>of(),
                                  Duration.ofDays(0), -1);
    }

    private static enum ReaderState {
        BEGINNING, METADATA, DURATION, STREAMS
    }

    public long totalFrames() {
        BigDecimal fps = videoStreams.stream().map(s -> s.fps).findFirst().orElse(new BigDecimal(25));
        return fps.multiply(new BigDecimal(duration.getSeconds())).longValue();
    }

    public static InputFileStats probeStats(String ffmpegPath, String inputFile) throws IOException, InterruptedException {
        ProcessBuilder ffmpegBuilder = new ProcessBuilder().command(ffmpegPath, "-i", "\"" + inputFile + "\"");
        Process ffmpeg = ffmpegBuilder.start();
        Scanner s = new Scanner(ffmpeg.getErrorStream());

        List<String> lines = new ArrayList<>();
        while (s.hasNextLine()) {
            lines.add(s.nextLine());
        }
        return fromLog(lines);
    }

    public static InputFileStats fromLog(List<String> lines) {
        ReaderState state = ReaderState.BEGINNING;
        int inputNumber = -1;
        String fileName = null;
        ImmutableMap.Builder<String, String> metadata = ImmutableMap.builder();
        ImmutableList.Builder<AudioStream> audioStreams = ImmutableList.builder();
        ImmutableList.Builder<VideoStream> videoStreams = ImmutableList.builder();
        int bitrate = -1;
        Duration duration = null;

        Pattern inputPattern = Pattern.compile("Input #(?<inputNumber>\\d),.*, from '(?<fileName>.*)'.*");
        Pattern durationPattern = Pattern.compile(" *Duration: (?<hours>\\d+):(?<minutes>\\d+):(?<seconds>\\d+)\\.(?<subseconds>\\d+), start: [\\d\\.]+, bitrate: (?<bitrate>\\d+) kb/s.*");
        Pattern audioPattern = Pattern.compile(" *Stream #\\d:(?<streamNumber>\\d).*: Audio:" +
                                                       " (?<codec>.*), (?<hz>\\d+) Hz, .*, .*, (?<bitrate>\\d+) kb/s");
        Pattern videoPattern = Pattern.compile(" *Stream #\\d:(?<streamNumber>\\d).*: Video: (?<codec>.*),.*," +
                                                       " (?<resolution>\\d+x\\d+).*, (?<fps>[\\d\\.]+) fps,.*");

        boolean done = false;
        for (int i = 0; i < lines.size() && !done; i++) {
            String line = lines.get(i);
            switch (state) {
                case BEGINNING:
                    Matcher matcher = inputPattern.matcher(line);
                    if (matcher.matches()) {
                        inputNumber = Integer.parseInt(matcher.group("inputNumber"));
                        fileName = matcher.group("fileName");
                        state = ReaderState.METADATA;
                        i++; // Skip Metadata line
                    }
                    break;
                case METADATA:
                    if (durationPattern.matcher(line).matches()) {
                        state = ReaderState.DURATION;
                        i--; // loop again on same line
                        continue;
                    }
                    String[] tokens = line.trim().split(":", 2);
                    metadata.put(tokens[0].trim(), tokens[1].trim());
                    break;
                case DURATION:
                    Matcher durationMatch = durationPattern.matcher(line);
                    if (durationMatch.matches()) {
                        duration = Duration.ofHours(Integer.parseInt(durationMatch.group("hours")))
                                                    .plusMinutes(Integer.parseInt(durationMatch.group("minutes")))
                                                    .plusSeconds(Integer.parseInt(durationMatch.group("seconds")))
                                                    .plusMillis(Integer.parseInt(durationMatch.group("subseconds")) * 100);
                        bitrate = Integer.parseInt(durationMatch.group("bitrate"));
                    }
                    state = ReaderState.STREAMS;
                    break;
                case STREAMS:
                    Matcher audioMatch = audioPattern.matcher(line);
                    if (audioMatch.matches()) {
                        int streamNumber = Integer.parseInt(audioMatch.group("streamNumber"));
                        String codec = audioMatch.group("codec");
                        int hz = Integer.parseInt(audioMatch.group("hz"));
                        int audioBitrate = Integer.parseInt(audioMatch.group("bitrate"));
                        audioStreams.add(new AudioStream(streamNumber, codec, hz, audioBitrate));
                        continue;
                    }
                    Matcher videoMatch = videoPattern.matcher(line);
                    if (videoMatch.matches()) {
                        int streamNumber = Integer.parseInt(videoMatch.group("streamNumber"));
                        String codec = videoMatch.group("codec");
                        String resolution = videoMatch.group("resolution");
                        BigDecimal fps = new BigDecimal(videoMatch.group("fps"));
                        videoStreams.add(new VideoStream(streamNumber, codec, resolution, fps));
                        continue;
                    }
                    if (line.contains("Metadata:")) {
                        done = true;
                    }
                    break;
            }
        }
        return new InputFileStats(inputNumber, fileName, metadata.build(), audioStreams.build(), videoStreams.build(),
                                  duration, bitrate);
    }
}