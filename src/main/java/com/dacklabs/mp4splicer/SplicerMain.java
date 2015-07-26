package com.dacklabs.mp4splicer;

import com.coremedia.iso.boxes.Container;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.Track;
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder;
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator;
import com.googlecode.mp4parser.authoring.tracks.AppendTrack;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SplicerMain {

    private static void p(String message) {
        System.out.println(LocalDateTime.now().format(DateTimeFormatter.ISO_TIME) + " - " + message);
    }

    public static void main(String[] args) throws IOException {
        List<Movie> movies = new ArrayList<>();

        if (args.length < 3) {
            p("Usage: splicer <output file path> <input file 1> <input file 2> [<additional input file>]...");
            p("Make sure you have at least 3 arguments, the output file and two inputs to concatenate (in order)." +
                      " Any additional arguments are interpreted as additional input files to concatenate.");
            System.exit(1);
        }

        List<String> argList = Arrays.asList(args);
        String outputFile = argList.get(0);
        p("Outputting to " + outputFile);

        for (String arg : argList.subList(1, argList.size())) {
            p("Adding movie " + arg);
            movies.add(MovieCreator.build(Paths.get(arg).normalize().toAbsolutePath().toString()));
        }

        List<Track> videoTracks = new ArrayList<>();
        List<Track> audioTracks = new ArrayList<>();

        for (Movie m : movies) {
            p("Splitting tracks...");
            for (Track track : m.getTracks()) {
                if (track.getHandler().equals("vide")) {
                    videoTracks.add(track);
                }
                if (track.getHandler().equals("soun")) {
                    audioTracks.add(track);
                }
            }
        }

        Movie concatMovie = new Movie();

        concatMovie.addTrack(new AppendTrack(videoTracks.toArray(new Track[videoTracks.size()])));
        concatMovie.addTrack(new AppendTrack(audioTracks.toArray(new Track[audioTracks.size()])));


        p("Creating output container " + outputFile);
        Container out2 = new DefaultMp4Builder().build(concatMovie);
        FileChannel fc = new RandomAccessFile(outputFile, "rw").getChannel();
        fc.position(0);
        p("Writing to container");
        out2.writeContainer(fc);
        p("Done.");
        fc.close();
    }
}
