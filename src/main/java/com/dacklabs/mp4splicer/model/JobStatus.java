package com.dacklabs.mp4splicer.model;

/**
* Created by david on 7/28/2015.
*/
public enum JobStatus {
    CREATED("Created"),
    ENCODING("Encoding input files"),
    CONCATENATING("Concatenating input files"),
    COPYING_OUTPUT("Copying output to destination directory"),
    DONE("Completed");

    public final String name;

    JobStatus(String name) {
        this.name = name;
    }
}
