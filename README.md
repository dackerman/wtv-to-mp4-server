# Movie Splicing Server

This is a server for concatenating and converting Windows Media WTV files into MP4 format.  It's main use is for when
we record long multi-hour shows (like the Wimbledon Finals) using Windows Media Player, but end up with massive blocks
of junk on the beginning and end (because we want some buffer in case the game starts early or ends late). However, this
takes up a ton of space and isn't worth keeping around. Secondly, for tournaments and other long videos, you generally
record multiple days or sessions, and might want to stick them together into one long video so that they can be archived
on a drive together.  This server accomplishes both goals by utilizing `ffmpeg` to both concatenate, trim, and convert
the files into `mp4` format for efficient and organized archival.

## Features
* Server that can browse it's local filesystem (including network-visible shares on other computers)
* Ability to concatenate any number of files in a directory, in any order
* Converts to `mp4` format
* Can do multiple jobs concurrently
* Can be run remotely so it doesn't take up local CPU/disk

## Usage / Overview
1. `gradle distZip`
1. unzip the distribution somewhere
1. `mp4-splicer-1.1/bin/mp4-splicer-1.1 -tmpDir <directory-for-temp-files> -ffmpeg <path-to-ffmpeg-executable>`
    1. `-tmpDir` _[required]_: Temporary directory to hold intermediate files
    1. `-ffmpeg` _[required]_: Path to your ffmpeg executable. Must be the 2015-08-01 version or newer
    1. `-port` _[optional]_: Specify a custom port for the server, defaults to 4567
1. Navigate to `localhost:4567` to see the current jobs. Click `Browse the filesystem` to create a new job
1. After adding 2 or more files,
    1. type a job name,
    1. the name of the output file,
    1. and potential trim values - a time to chop off from the beginning and end of the resulting file
1. click "Create Job" and it will take you back to the main page
1. click on your job name to see statistics about the job, and how it's running.

## Known Issues
* Choppiness between concatenated files
* Can't restart video processing
* Resumes from the beginning after a failure
* Browsing starts at "\\CENTERCOURT\videotest" because that's a machine on my local network
* Need to specify FFMPEG on the path
* logs/ directory doesn't get created automatically
* Cancelling sometimes doesn't work on the first click

## Future Work
* Restarting jobs
* Capping number of concurrent jobs
* Better monitoring of progress (streaming charts, etc)
* Better browser that doesn't require knowing about the URL format
* Distributed computation so multiple computers can participate in the process
* More types of transformations than just wtv -> mpeg with concat
* Ability to preview or see where you're about to trim videos

## MIT License
    The MIT License (MIT)

    Copyright (c) 2015 David Ackerman

    Permission is hereby granted, free of charge, to any person obtaining a copy
    of this software and associated documentation files (the "Software"), to deal
    in the Software without restriction, including without limitation the rights
    to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
    copies of the Software, and to permit persons to whom the Software is
    furnished to do so, subject to the following conditions:

    The above copyright notice and this permission notice shall be included in
    all copies or substantial portions of the Software.

    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
    IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
    FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
    AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
    LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
    OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
    THE SOFTWARE.