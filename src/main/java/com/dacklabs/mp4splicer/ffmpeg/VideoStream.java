package com.dacklabs.mp4splicer.ffmpeg;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
* Created by david on 8/5/2015.
*/
public class VideoStream {
    public final int streamNumber;
    public final String codec;
    public final String resolution;
    public final BigDecimal fps;

    @JsonCreator
    public VideoStream(@JsonProperty("streamNumber") int streamNumber,
                       @JsonProperty("codec") String codec,
                       @JsonProperty("resolution") String resolution,
                       @JsonProperty("fps") BigDecimal fps) {
        this.streamNumber = streamNumber;
        this.codec = codec;
        this.resolution = resolution;
        this.fps = fps;
    }
}
