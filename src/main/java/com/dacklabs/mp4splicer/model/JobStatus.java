package com.dacklabs.mp4splicer.model;

/**
* Created by david on 7/28/2015.
*/
public enum JobStatus {
    CREATED("Created (step 1/3)"),
    ENCODING("Encoding (step 2/3)"),
    DONE("Completed (step 3/3)"),
    CANCELED("Canceled");

    public final String name;

    JobStatus(String name) {
        this.name = name;
    }
}
