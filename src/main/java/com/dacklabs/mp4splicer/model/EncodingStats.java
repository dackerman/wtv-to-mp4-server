package com.dacklabs.mp4splicer.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class EncodingStats {
    public final int frame;
    public final double fps;
    public final String sizeInKb;
    public final String estimatedTimeLeft;
    public final String bitrate;
    public final int droppedFrames;

    @JsonCreator
    public EncodingStats(@JsonProperty("frame") int frame,
                         @JsonProperty("fps") double fps,
                         @JsonProperty("sizeInKb") String sizeInKb,
                         @JsonProperty("estimatedTimeLeft") String estimatedTimeLeft,
                         @JsonProperty("bitrate") String bitrate,
                         @JsonProperty("droppedFrames") int droppedFrames) {
        this.frame = frame;
        this.fps = fps;
        this.sizeInKb = sizeInKb;
        this.estimatedTimeLeft = estimatedTimeLeft;
        this.bitrate = bitrate;
        this.droppedFrames = droppedFrames;
    }

    public static EncodingStats none() {
        return new EncodingStats(0, 0, "0kB", "N/A", "N/A", 0);
    }
}
