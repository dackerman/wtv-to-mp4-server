package com.dacklabs.mp4splicer.model;

import com.dacklabs.mp4splicer.ffmpeg.InputFileStats;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class InputFile {
    public final String path;
    public final InputFileStats stats;

    @JsonCreator
    public InputFile(@JsonProperty("path") String path,
                     @JsonProperty("stats") InputFileStats stats) {
        this.path = path;
        this.stats = stats;
    }

    public InputFile withProbedStats(InputFileStats stats) {
        return new InputFile(path, stats);
    }

    public static InputFile create(String path) {
        return new InputFile(path, InputFileStats.none());
    }
}
