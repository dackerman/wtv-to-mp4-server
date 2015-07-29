package com.dacklabs.mp4splicer.model;

/**
* Created by david on 7/28/2015.
*/
public class EncodingStats {
    public final int frame;
    public final double fps;
    public final String sizeInKb;
    public final String estimatedTimeLeft;
    public final String bitrate;
    public final int droppedFrames;

    public EncodingStats(int frame, double fps, String sizeInKb, String estimatedTimeLeft, String bitrate,
                         int droppedFrames) {
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
