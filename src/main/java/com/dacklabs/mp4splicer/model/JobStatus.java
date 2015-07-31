package com.dacklabs.mp4splicer.model;

/**
* Created by david on 7/28/2015.
*/
public enum JobStatus {
    CREATED("Created (step 1/5)"),
    ENCODING("Encoding input files (step 2/5)"),
    CONCATENATING("Concatenating input files (step 3/5)"),
    COPYING_OUTPUT("Copying output to destination directory (step 4/5)"),
    DONE("Completed (step 5/5)"),
    CANCELED("Canceled");

    public final String name;

    JobStatus(String name) {
        this.name = name;
    }
}
