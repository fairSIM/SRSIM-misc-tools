OMX OTF files documentation:

- OMX has a tool to generate OTFs from bead measurements

- Gustafsson2008 contains how its done

- Boils down to: measure (small enough) beads, band seperate, FFT, divide by FFT of bead

- The tool saves a *.otf file for each measurement, these are in DV-file format

- Try renaming them to .dv, standard Fiji / BioFormats will read them, but not quite correctly

- This is just because they contain complex numbers (makes sense for OTFs)

- Bioformats even knows this (filetype=4 means complex), but ImageJ and complex numbers...

- So, re-implemented a minimal dv-Reader to handle that

- Wrapped that in a small Fiji plugin that displays and saves these OTFs

- Current version can also compute the 2D projection and save it as XML
 

