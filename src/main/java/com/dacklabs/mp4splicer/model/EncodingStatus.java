package com.dacklabs.mp4splicer.model;

/**
* Created by david on 7/28/2015.
*/
public enum EncodingStatus {
    WAITING("Waiting"),
    ENCODING("Encoding"),
    DONE("Done");

    public final String displayName;

    EncodingStatus(String displayName) {
        this.displayName = displayName;
    }
}
