package com.dacklabs.mp4splicer.model;

/**
* Created by david on 7/28/2015.
*/
public enum JobStatus {
    CREATED("Created"),
    ENCODING("Encoding"),
    DONE("Completed"),
    CANCELED("Canceled");

    public final String name;

    JobStatus(String name) {
        this.name = name;
    }
}
