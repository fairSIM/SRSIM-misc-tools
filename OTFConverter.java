package de.bio_photonics.omxtools;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;


// TODO: merge this with the OTF_Reader plugin at some point

/** Plugin to read the OMX OTF files. */
public class OTFConverter {

    final float [][] bandsData;
    final int width, height, nrImg, fType;
    final float micronPerPxlLateral, micronPerPxlAxial;
    

    /** Read in the OMX OTF from file.
     *
     *	For all the header bytes and magic number, see 
     *	'OMX-OTFs-Readme.md' and e.g. here:
     *	
     *	https://github.com/openmicroscopy/bioformats/blob/v5.1.8/components/formats-gpl/src/loci/formats/in/DeltavisionReader.java
     *	https://www.openmicroscopy.org/site/support/bio-formats5.1/formats/deltavision.html
     *	http://rsb.info.nih.gov/ij/plugins/track/delta.html
     *
     *  */
    public OTFConverter(File fObj) 
	throws java.io.IOException {
	
	// open file for reading
	ByteBuffer otfImg;
	{
	    RandomAccessFile rd = new RandomAccessFile( fObj , "r");
	    byte [] data = new byte[ (int)rd.length() ];
	    rd.read( data );
	    otfImg = ByteBuffer.wrap( data );
	    short filestamp=(short)(((data[96]&0xff)<<8)|(data[97]&0xff));
	    
	    log("Endian: "+filestamp+" "+(short)0xc0a0); 
	    otfImg.order( java.nio.ByteOrder.LITTLE_ENDIAN );
	}

	
	// First 4 Ints: w, h, #images, plx_type
	width  = otfImg.getInt(0); 
	height = otfImg.getInt(4); 
	nrImg = otfImg.getInt(8); 
	fType = otfImg.getInt(12);
	
	// At byte #92:  size of extended header
	final int extHeader = otfImg.getInt(92);

	// try to read in pixel size also
	micronPerPxlLateral = otfImg.getFloat( 44 );
	micronPerPxlAxial   = otfImg.getFloat( 40 );

	log("w,h,nrImg,Type, lenHeader :"+width+" "+height+" "+nrImg
		+" "+fType+" "+extHeader);
	log(String.format(" pxl size, lateral: %6.4f  axial: %6.4f ",
		micronPerPxlLateral, micronPerPxlAxial));
	
	if ((nrImg!=3) || (fType!=4)) {
	    throw new java.io.IOException("#Images != 3 or pxlType!=cplx\nSeems no OMX OTF file");
	}

	// std header is 1024 bytes, offset that plus ext header size
	final int startPxl = extHeader + 1024;
	otfImg.position( startPxl );

	// loop images (should be 3  bands with w*h [real,cplx] each)
	bandsData = new float[3][ width*height*2 ];
	
	for (int band=0; band<3; band++) {
	    for ( int y=0;y<height;y++)
	    for ( int x=0;x<width;x++) {
		bandsData[band][ (x*height+y)*2 + 0 ] = otfImg.getFloat();
		bandsData[band][ (x*height+y)*2 + 1 ] = otfImg.getFloat();
	    }
	}
    }


    // some simple access routines
    public float getReal( int band, int lateral, int axial ) {
	return bandsData[band][ 2* ( lateral + axial*height )];
    }
    
    public float getImag( int band, int lateral, int axial ) {
	return bandsData[band][ 2* ( lateral + axial*height )+1];
    }


    // Some not-so-clever logging interface

    /** Implement this for log output */
    public interface Logger {
	public void log(String whatToLog);
    };

    /** If and where to log to. (set to 'null' to disable logging) */
    public static void setLogger( Logger l ) {
	ourLogger = l;
    }

    /** Reset the log to go to System.out.println */
    public static void resetLogger() {
	ourLogger = new  Logger() {
	    @Override
	    public void log( String what ) {
		System.out.println(what);
	    }
	};
    }


    private static Logger ourLogger = new  Logger() {
	    @Override
	    public void log( String what ) {
		System.out.println(what);
	    }
	};
    
    private static void log(String what) {
	if (ourLogger!=null) {
	    ourLogger.log( what );
	}
    }

    // this could / should become an interface at some point

    public float [][] getBandsData() {
	return bandsData;
    }

    public int getSamplesLateral() {
	return height;
    }

    public int getSamplesAxial() {
	return width;
    }

    public int getNrBands() {
	return nrImg;
    }

    public double getCyclesPerMicronLateral() {
	return micronPerPxlLateral;
    }

    public double getCyclesPerMicronAxial() {
	return micronPerPxlAxial;
    }



}
