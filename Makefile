# Rudimentary makefile
# assumes 'javac6' points to a java compiler vers. 6
# on your system, to get easy backwards compatibility
# with ImageJ / FIJI

.PHONY: all clean jar

all:
	javac6 -extdirs ./external -d ./ *.java

jar:	all
	jar -cf SRSIM_MiscTools.jar plugins.config de/* 

clean:
	rm -rf ./de *.jar
