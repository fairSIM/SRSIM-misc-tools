package de.bio_photonics.omxtools;

//import org.fairsim.utils.Conf;

import ij.plugin.PlugIn;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;
import ij.process.FloatProcessor;
import ij.gui.GenericDialog;
import ij.plugin.ZProjector;
import ij.gui.GenericDialog;

import javax.swing.JFileChooser;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;


/** Plugin to read the OMX OTF files */
public class OTF_Reader implements PlugIn {


    // all options can be set by dialog via 'showDialog()'
    boolean doMirror= true;		// mirror OTF to neg. x/y axis
    boolean doLogPw = true;		// display pwSpec logarithmic
    boolean doCenterDcZ = true;		// wrap the DC peak to center of y-axis
    boolean doDisplayRaw = false;	// display raw input data
    boolean doDisplayPhases = false;	// add the phases
    boolean doSaveProjection = false;	// save the z-projection to text file
    int     wavelength = 515;		// emission wavelength
    boolean doSave3d = false;		// save full 3d OTF

    static int verbose = 0;			// verbosity of (debug) output

    @Override
    public void run( String arg ) {

	File fObj = null;

	{
	JFileChooser fc = new JFileChooser();
	int returnVal = fc.showOpenDialog(IJ.getInstance());

        if (returnVal == JFileChooser.APPROVE_OPTION) {
            fObj = fc.getSelectedFile();
        }
	else {
	    return;
	}
	}

	if (showDialog()==-1) return;

	// read in raw OTFs
	FloatProcessor [] rawOtfs = null;
	try {
	    rawOtfs = readOTFs( fObj );
	} catch (java.io.IOException e) {
	    IJ.showMessage("ERR: "+e);
	    return;
	}
	

	// save their 2d projection
	if (doSaveProjection) {
	    JFileChooser fc = new JFileChooser();
	    int returnVal = fc.showSaveDialog(IJ.getInstance());

	    File fSaveObj = null;
	    if (returnVal == JFileChooser.APPROVE_OPTION) {
		fSaveObj = fc.getSelectedFile();
	    }
	    else {
		return;
	    }

	    try {
		saveOTFprojection( rawOtfs, fSaveObj );
	    } catch (Exception e) {
		IJ.showMessage("Error saving: "+e);
		return;
	    }
	}
	
	// display OTFs power spectra
	displayOTFs( rawOtfs ); 

    }


    /** Display OTFs and their power spectra.
     *  This reads some global variables as to what exactly
     *  to add to the output */
    void displayOTFs( FloatProcessor [] raw ) {
	final int w=raw[0].getWidth(), h=raw[0].getHeight();
	
	// output raw, 3 bands x 2 images (re and im)
	if (doDisplayRaw) {
	    ImageStack rawSt = new ImageStack( w,h);
	    for (int b=0;b<3;b++)
	    for (int c=0;c<2;c++) {
		String label = "band "+b+" ("+((c==0)?("re"):("im"))+")";
		rawSt.addSlice(label, raw[2*b+c]);
	    }
	    ImagePlus ipRaw = new ImagePlus("raw OTF", rawSt);
	    ipRaw.show();
	}

	// output power spectra
	int outW = (doMirror)?(2*h):(h);
	int outH = w;
	ImageStack pwSt = new ImageStack( outW,w);
	
	for (int b=0;b<3;b++) {

	    FloatProcessor outMag = new FloatProcessor(outW,outH);
	    FloatProcessor outPha = new FloatProcessor(outW,outH);
	    
	    for (int y=0;y<h;y++)
	    for (int x=0;x<w;x++) {
		float re = raw[2*b+0].getf(x,y);
		float im = raw[2*b+1].getf(x,y);
		
		float mag = (float)Math.hypot( re,im );
		float pha = (float)Math.atan2( im,re );

		// if asked, calc log of spectrum
		if (doLogPw) {
		    double t=1e-3;
		    mag = (float)((mag>=t)?(Math.log(mag)):(Math.log(t)));
		}

		// center the z-DC peak in y
		int oy = (doCenterDcZ)?( (x+w/2)%w ):(x);
		// if mirror, offset x coordinate to center
		int ox = y+((doMirror)?(outW/2):(0));

		// add values for pos x/y position
		outMag.setf( ox, oy, mag ); 
		outPha.setf( ox, oy, pha ); 
		
		// if requested, add values to neg. x/y-axis
		if (doMirror) {
		    outMag.setf( outW-ox, oy, mag ); 
		    outPha.setf( outW-ox, oy, pha ); 
		}

	    }

	    String label ="band "+b+" (mag"+( (doLogPw)?(",log)"):(")") );
	    pwSt.addSlice(label, outMag);
	    if (doDisplayPhases)
		pwSt.addSlice("band "+b+" (phase)", outPha);	
	}

	ImagePlus ipPw = new ImagePlus("OTF power spec.", pwSt );
	ipPw.show();

    }


    /** Returns 6 FloatProcessors containing the OTFs that
     *  where read in. Two processors per band 
     *  (real and imag part), three bands. 
     *
     *	For all the header bytes and magic number, see 
     *	'OMX-OTFs-Readme.md' and e.g. here:
     *	
     *	https://github.com/openmicroscopy/bioformats/blob/v5.1.8/components/formats-gpl/src/loci/formats/in/DeltavisionReader.java
     *	https://www.openmicroscopy.org/site/support/bio-formats5.1/formats/deltavision.html
     *	http://rsb.info.nih.gov/ij/plugins/track/delta.html
     *
     *  */
    static public FloatProcessor [] readOTFs(File fObj) 
	throws java.io.IOException {
	
	FloatProcessor [] ret= new FloatProcessor[6];
	OTFConverter otfConverter = new OTFConverter( fObj);

	final int w = otfConverter.width;
	final int h = otfConverter.height;

	for (int band=0; band<3; band++) {
	    ret[band*2+0] = new FloatProcessor(w,h);
	    ret[band*2+1] = new FloatProcessor(w,h);

	    for ( int y=0;y<h;y++)
	    for ( int x=0;x<w;x++) {
		ret[band*2+0].setf( x,y, otfConverter.getReal(band,y,x) );
		ret[band*2+1].setf( x,y, otfConverter.getImag(band,y,x) );
	    }

	}
	
	return ret;
    }


    /** Calculate and store the 2d projection of the OTF */
    void saveOTFprojection(FloatProcessor [] raw, File fObj) 
	throws IOException, Conf.SomeIOException {
	
	
	//FileWriter fr = new FileWriter( fObj);
	//fr.write("# OTF sizes xy "+w+", sum z over "+h+"\n");
	final int w=raw[0].getWidth(), h=raw[0].getHeight();

	Conf cfg = new Conf("fairsim");
	Conf.Folder otf = cfg.r().mk("otf2d");


	otf.newDbl("NA").setVal( 1.4 );
	otf.newInt("emission").setVal( wavelength );
	
	Conf.Folder data =otf.mk("data");

	data.newInt("bands" ).setVal(3);
	data.newDbl("cycles").setVal( 0.048828 );
	data.newInt("samples" ).setVal(h);

	byte [][] band   = new byte[3][ 2 * 4 * h ];
	byte [][] band3d = new byte[3][ 2 * 4 * h * w ];
	FloatBuffer [] fb   = new FloatBuffer[3];
	FloatBuffer [] fb3d = new FloatBuffer[3];
	for (int i=0; i<3; i++) {
	      fb[i] = ByteBuffer.wrap(  band[i]).asFloatBuffer();
	    fb3d[i] = ByteBuffer.wrap(band3d[i]).asFloatBuffer();
	}


	// sum up 2D projection
	for (int xy=0;xy<h;xy++) {
	    float [][] sum = new float[3][2];
	    
	    // loop bands, re/im
	    for (int b=0;b<3;b++)
	    for (int c=0;c<2;c++) {
		// sum z
		for (int z=0;z<w;z++) {
		    sum[b][c] += raw[b*2+c].getf(z,xy);
		}
		// add to output
		fb[b].put(sum[b][c]);
	    }
	}
	
	// collect 3D data
	for (int b=0;b<3;b++)	// band
	for (int z=0;z<w;z++)	// axial
	for (int xy=0;xy<h;xy++)   // lateral
	for (int c=0;c<2;c++) {	    // re/im
	    fb3d[b].put( 2*(z*h+xy) + c, raw[b*2+c].getf(z,xy) );
	}

	data.newData("band-0").setVal( band[0] );
	data.newData("band-1").setVal( band[1] );
	data.newData("band-2").setVal( band[2] );

	if (doSave3d) {
	    Conf.Folder otf3d = cfg.r().mk("otf3d");
	    otf3d.newDbl("NA").setVal( 1.4 );
	    otf3d.newInt("emission").setVal( wavelength );
	    
	    Conf.Folder data3d =otf3d.mk("data");

	    data3d.newInt("bands" ).setVal(3);
	    data3d.newInt("samples-axial" ).setVal(w);
	    data3d.newInt("samples-lateral" ).setVal(h);
	    data3d.newDbl("cycles-lateral").setVal( 0.048828 );
	    data3d.newDbl("cycles-axial").setVal( 0.12307 );
	
	    data3d.newData("band-0").setVal( band3d[0] );
	    data3d.newData("band-1").setVal( band3d[1] );
	    data3d.newData("band-2").setVal( band3d[2] );
	}



	//fr.close();
	cfg.saveFile( fObj.getAbsolutePath());
	
    }




    /** Shows a dialog to set our global options */
    int showDialog() {

	GenericDialog gd = new GenericDialog("OMX OTF reader");
	
	gd.addMessage("--- Options for power spectra ---");
	gd.addCheckbox("Show logarth. spectrum", doLogPw);
	gd.addCheckbox("Output phases", doDisplayPhases );
	gd.addCheckbox("Center DC for z-axis", doCenterDcZ );
	gd.addCheckbox("Add negative xy-axis", doMirror );
	gd.addMessage("--- Options for other output ---");
	gd.addCheckbox("Show raw data (extra stack)", doDisplayRaw );
	gd.addCheckbox("Store the OTF", doSaveProjection );
	gd.addCheckbox("Store the 3D OTF (also stored 2D)", doSave3d );
	gd.addNumericField("Emission wavelength", 515, 0 );

	gd.showDialog();
	if (gd.wasCanceled()) 
	    return -1;
	
	doLogPw = gd.getNextBoolean();
	doDisplayPhases = gd.getNextBoolean();
	doCenterDcZ = gd.getNextBoolean();
	doMirror = gd.getNextBoolean();
	doDisplayRaw = gd.getNextBoolean();
	doSaveProjection = gd.getNextBoolean();
	doSave3d = gd.getNextBoolean();
	doSaveProjection |= doSave3d;
	wavelength = (int)gd.getNextNumber();
	
	return 0;
    }


    /** Read a .otf file for testing */
    public static void main( String [] arg ) throws IOException {
	OTF_Reader otfR = new OTF_Reader();

	otfR.verbose = 1;
	FloatProcessor [] otfs = otfR.readOTFs( new File(arg[0]));
	otfR.doDisplayRaw = true;
	otfR.displayOTFs( otfs );
    }


}
