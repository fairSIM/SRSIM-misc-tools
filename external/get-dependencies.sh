#!/bin/bash

fileMissing=0

# Get the ImageJ base library (in version 1.47, which is the lowest we currently support)
if [ ! -e ij147v.jar ] ; then
    fileMissing=1
    wget https://imagej.nih.gov/ij/download/jars/ij147v.jar
fi


# This fetches the java 1.6 runtime, needed for backwards-compatible
# compilation to Java 6 with newer compilers
#   unfortunately, Java 6's rt.jar does not seem to be on Maven, so
#   fetch it by extracting it from Ubuntu's openjdk deb.
#   Java 9 will make it much more easier with the "-release" option
if [ ! -e rt-1.6.jar ] ; then
    fileMissing=1

    mkdir tmp-rt-jar
    cd tmp-rt-jar

    # download the 'openjdk-6-jre-headless' deb file
    wget http://security.ubuntu.com/ubuntu/pool/universe/o/openjdk-6/openjdk-6-jre-headless_6b41-1.13.13-0ubuntu0.14.04.1_amd64.deb -O openjdk.deb


    # extract the deb
    echo "Extracting rt.jar from the .deb file"
    echo "This might take a few moments... "
    ar -x openjdk.deb

    # extract the rt.jar from the data.tar
    tar -xf data.tar.xz ./usr/lib/jvm/java-6-openjdk-amd64/jre/lib/rt.jar
    mv ./usr/lib/jvm/java-6-openjdk-amd64/jre/lib/rt.jar ../rt-1.6.jar
    cd ..

    rm -rf tmp-rt-jar
    echo "Done."

fi

if [ $fileMissing -eq 0 ] ; then
    echo "All files found, fairSIM should compile"
fi

