# DES Supply Chain Models

This repository contains the three primary DES Supply Chain models, plus additional models developed along the way.  The models are located in the src directory: they are the pharma3, sc2, and sc3 models respectively.


### build.xml
The control script for building the project from the source code using Apache Ant

### config/
Configuration files for each models, and disruption scenario files

### doc/
All documentation.

### gnu/
Gnuplot command scripts, for use in visualization

### bin/
Various shell scripts to run the models.  The important ones are:

- run3.sh  -- main run script for PHARMA3 ("SC-1")
- run-sc2.sh -- main run script for SC-2
- run-sc3.sh -- main run script for SC-3
- pop-run.sh -- a test script for a simple model (just to make sure MASON is properly installed).

### paramfiles/
Control files for ECJ optimization files (from Raj)

### out/
A place for output files

### scripts/ -- other shell scripts (e.g. for multiple runs)
### src/ -- the Java source code for all applications

