package com.dacklabs.mp4splicer.ffmpeg;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class AudioStream {
    public final int streamNumber;
    public final String codec;
    public final int hz;
    public final int bitrate;

    @JsonCreator
    public AudioStream(@JsonProperty("streamNumber") int streamNumber,
                       @JsonProperty("codec") String codec,
                       @JsonProperty("hz") int hz,
                       @JsonProperty("bitrate") int bitrate) {
        this.streamNumber = streamNumber;
        this.codec = codec;
        this.hz = hz;
        this.bitrate = bitrate;
    }
}
