package com.dacklabs.mp4splicer.model;

public enum EncodingStatus {
    WAITING("Waiting"),
    ENCODING("Encoding"),
    DONE("Done");

    public final String displayName;

    EncodingStatus(String displayName) {
        this.displayName = displayName;
    }
}
