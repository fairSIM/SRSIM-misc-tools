# Rudimentary makefile
# assumes 'javac6' points to a java compiler vers. 6
# on your system, to get easy backwards compatibility
# with ImageJ / FIJI

.PHONY: all clean jar

all:
	javac -source 1.6 -target 1.6 -bootclasspath ./external/rt-1.6.jar  -extdirs ./external -d ./ src/main/java/de/bio_photonics/*/*.java

jar:	all
	cp src/main/resources/plugins.config ./
	jar -cf SRSIM_MiscTools.jar plugins.config de/* 
	rm plugins.config

clean:
	rm -rf ./de *.jar
