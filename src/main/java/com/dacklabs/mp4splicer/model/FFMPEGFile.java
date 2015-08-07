package com.dacklabs.mp4splicer.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class FFMPEGFile {
    public final String path;
    public final EncodingStatus encodingStatus;

    public static FFMPEGFile create(String path) {
        return new FFMPEGFile(path, EncodingStatus.WAITING);
    }

    @JsonCreator
    public FFMPEGFile(@JsonProperty("path") String path,
                      @JsonProperty("encodingStatus") EncodingStatus encodingStatus) {
        this.path = path;
        this.encodingStatus = encodingStatus;
    }

    public FFMPEGFile transitionTo(EncodingStatus newStatus) {
        return new FFMPEGFile(path, newStatus);
    }

    public double percentComplete() {
        switch (encodingStatus) {
            case WAITING:
                return 0;
            case ENCODING:
                return 50;
            case DONE:
                return 100;
        }
        return 0;
    }
}
