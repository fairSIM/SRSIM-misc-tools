package de.bio_photonics.omxtools;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;
import ij.process.FloatProcessor;
import ij.process.ShortProcessor;

import ij.plugin.PlugIn;
import ij.IJ;
import ij.gui.GenericDialog;

/** Small utility class to split Zeiss stacks into
2D-planes for analysis */
public class Zeiss_Splitter implements PlugIn {
	
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
		if ((numImages <25)||(numImages%25)!=0) {
			IJ.showMessage("Zeiss stack should be n*25 Images");
			return;
		}

		// check the image format
		if (( aip.getType() != ImagePlus.GRAY8 )  &&
			( aip.getType() != ImagePlus.GRAY16 ) &&
			( aip.getType() != ImagePlus.GRAY32 )    ) {
				IJ.showMessage("Zeiss stack should be a grayscale image");
				return ;
			}

		// get parameters
		GenericDialog gd2 = new GenericDialog("Slice selector");
		gd2.addNumericField("Slice [1-" +(numImages/25)+"]",1,0);
		gd2.showDialog();
		if (gd2.wasCanceled()) return;
		final int idx = (int)gd2.getNextNumber();

		if ((idx<=0)||(idx > (numImages/25))) {
		    IJ.showMessage("Index out of range");
		    return;
		    }
		
		// do the work
		ImageStack res = get2Dstack( aip.getStack() , idx-1 );
		ImagePlus result = new ImagePlus(  "Zeiss plane "+idx, res);
		result.show();

	}

	/** Copies a 2D-slice out of a stack */
	public ImageStack get2Dstack( ImageStack in3Dstack , int slice ) {

		// create a new stack
		final int w= in3Dstack.getWidth();
		final int h= in3Dstack.getHeight();
		ImageStack newStack = new ImageStack( w, h);
		final int numImages = in3Dstack.getSize();
		final int zLen = numImages/25;

		// collect the images
		for (int angle=0; angle<5; angle++)
		for (int pha=0; pha<5; pha+=1) {
			final int pos = pha*(zLen*5) + (angle*zLen) + slice;
			newStack.addSlice( in3Dstack.getProcessor(pos+1).duplicate());
		}

		// return result	
		return newStack;	
	}
	

}
