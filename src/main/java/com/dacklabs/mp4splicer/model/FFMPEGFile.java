package com.dacklabs.mp4splicer.model;

/**
* Created by david on 7/28/2015.
*/
public class FFMPEGFile {
    public final String path;
    public final EncodingStatus encodingStatus;
    public final EncodingStats stats;

    public static FFMPEGFile create(String path) {
        return new FFMPEGFile(path, EncodingStatus.WAITING, EncodingStats.none());
    }

    public FFMPEGFile(String path, EncodingStatus encodingStatus, EncodingStats stats) {
        this.path = path;
        this.encodingStatus = encodingStatus;
        this.stats = stats;
    }
}
