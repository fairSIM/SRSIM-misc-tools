package de.bio_photonics.omxtools;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;
import ij.process.FloatProcessor;
import ij.process.ShortProcessor;

import ij.plugin.PlugIn;
import ij.IJ;
import ij.gui.GenericDialog;

/** Small utility class to split OMX stacks into
2D-planes for analysis */
public class OMX_Splitter implements PlugIn {
	
	@Override
	public void run(String arg) {
	
		// get the active image plus instance
		ImagePlus aip = ij.WindowManager.getCurrentImage();
		if (aip == null) {
			IJ.showMessage("No active image stack selected");
			return;
		}

		// currently: check if these are 3x 5 phases
		int numImages = aip.getStack().getSize();
		if ((numImages <3)||(numImages%15)!=0) {
			IJ.showMessage("OMX stack should be n*15 Images");
			return;
		}

		// check the image format
		if (( aip.getType() != ImagePlus.GRAY8 )  &&
			( aip.getType() != ImagePlus.GRAY16 ) &&
			( aip.getType() != ImagePlus.GRAY32 )    ) {
				IJ.showMessage("OMX stack should be a grayscale image");
				return ;
			}

		// display dialog
		GenericDialog gd = new GenericDialog("OMX stack splitter");
		String [] whatToDo = { "2D slice as stack", "sep. angles, phases summed" };
		gd.addChoice("What to do", whatToDo, whatToDo[1]);
		gd.showDialog();
      		if (gd.wasCanceled()) return;

		// find out what to do ...
		int sel = gd.getNextChoiceIndex();

		// ... seperate out a 2D-slice
		if (sel == 0) {
		    // get parameters
		    GenericDialog gd2 = new GenericDialog("Slice selector");
		    gd2.addNumericField("Slice [1-" +(numImages/15)+"]",1,0);
		    gd2.addCheckbox("zero padding", false);
		    gd2.addNumericField("padding factor",2,0);
		    gd2.showDialog();
		    if (gd2.wasCanceled()) return;
		    final int idx = (int)gd2.getNextNumber();
		    final boolean zeroPad = gd2.getNextBoolean();
		    final int padf = (int)gd2.getNextNumber();

		    if ((idx<=0)||(idx > (numImages/15))) {
			IJ.showMessage("Index out of range");
			return;
			}

		    
		    // do the work
		    ImageStack res = get2Dstack( aip.getStack() , idx-1 , zeroPad,padf);
		    ImagePlus result = new ImagePlus(  "OMX plane "+idx, res);
		    result.show();


		}

		// ... seperate out the angles
		if (sel == 1) {
		    
		    // do the work
		    ImageStack [] res = seperateAngles( aip.getStack() );
		    for (int ang=0;ang<3;ang++) {
			ImagePlus result = new ImagePlus(  "OMX angle "+ang, res[ang]);
			result.show();
		    }

		}


	}

	/** Copies a 2D-slice out of a stack */
	public ImageStack get2Dstack( ImageStack in3Dstack , int slice , boolean zeroPad, final int f ) {

		// create a new stack
		final int w= in3Dstack.getWidth();
		final int h= in3Dstack.getHeight();
		ImageStack newStack = new ImageStack( (zeroPad)?(f*w):(w), (zeroPad)?(f*h):(h));
		final int numImages = in3Dstack.getSize();


		// collect the images
		for (int angle=0; angle<3; angle++)
		for (int pha=0; pha<5; pha+=1) {
			final int pos = (angle*numImages/3) + (slice*5) + pha;
			if (!zeroPad)
			    newStack.addSlice( in3Dstack.getProcessor(pos+1).duplicate());
			else { 
			    ShortProcessor sp = new ShortProcessor(w*f,h*f);
			    ImageProcessor in = in3Dstack.getProcessor(pos+1);
			    // copy the data
			    for (int y=0;y<h;y++)
			    for (int x=0;x<w;x++)
				sp.setf(x+(f-1)*(w/2),y+(f-1)*(h/2),in.getf(x,y));
			    // fade the edges
			    


			    newStack.addSlice( sp );
			}

		}

		// return result	
		return newStack;	
	}
	
	/** Returns 3 stacks, one per angle, phases summed up */
	public ImageStack [] seperateAngles( ImageStack inStack ) {

	    final int zDepth = inStack.getSize()/15;

	    // allocate stacks
	    ImageStack [] ret = new ImageStack[3];
	    for (int ang = 0; ang<3; ang++)
		ret[ang] = new ImageStack( inStack.getWidth(), inStack.getHeight());

	    // loop z and angle
	    for (int z = 0; z<zDepth; z++) 
	    for (int ang = 0; ang<3; ang++) {
		// add a phase projection
		ret[ang].addSlice( "z="+(z+1) , sumUp( inStack, (ang*zDepth+z)*5 , 5) );

	    }

	    // return result(s)
	    return ret;

	}


	/** Summed up [n,n+m] images (helper function). */
	ImageProcessor sumUp( ImageStack in, int n , int m ) {

	    
	    final int w = in.getProcessor(1).getWidth();
	    final int h = in.getProcessor(1).getHeight();
	    FloatProcessor ret = new FloatProcessor(w,h);
	    
	    // loop the sum
	    for (int i=n; i<n+m; i++) {

		ImageProcessor cur = in.getProcessor(i+1);
		for (int y=0;y<h;y++)
		for (int x=0;x<w;x++)
		    ret.setf( x,y, ret.getf( x,y) + cur.getf(x,y));
	    }
		
	    return ret;


	}

	/** Fade the edges of an ImageProcessor (for FFT with zero padding) */
	public static void fadeEdges( ImageProcessor ip , int px, final int w, final int h ) {
	
	    final double fac = 1./px * Math.PI/2.;
	    
	    // top
	    for (int y=0; y<px; y++)
		for (int x=0; x<w ; x++)
		    ip.setf( x,y, ip.getf(x,y)* (float)Math.pow( Math.sin( y * fac ) , 2 ));
	    // bottom
	    for (int y=h-px; y<h; y++)
		for (int x=0; x<w ; x++)
		    ip.setf( x,y, ip.getf(x,y)* (float)Math.pow( Math.sin( (h-y-1) * fac ) , 2 ));
	    // left
	    for (int y=0; y<h; y++)
		for (int x=0; x<px ; x++)
		    ip.setf( x,y, ip.getf(x,y)* (float)Math.pow( Math.sin( x * fac ) , 2 ));
	    // right
	    for (int y=0; y<h; y++)
		for (int x=w-px; x<w ; x++)
		    ip.setf( x,y, ip.getf(x,y)* (float)Math.pow( Math.sin( (w-x-1) * fac ) , 2 ));
	}
}
