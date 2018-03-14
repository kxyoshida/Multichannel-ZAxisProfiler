//package ij.plugin;
import ij.plugin.*;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.measure.*;
import ij.plugin.filter.Analyzer;
import ij.util.Tools;
import java.awt.*;
import ij.plugin.ZAxisProfiler;

/** Extends the Image/Stacks/Plot Z-axis Profile command, 
	which plots the selection mean gray value versus slice number.
*/
public class MultiCh_ZAxisProfiler extends ZAxisProfiler {
    
	private static String[] choices = {"time", "z-axis"};
	private static String choice = choices[0];
	private boolean showingDialog;
	private ImagePlus imp;
	private boolean isPlotMaker;
	private boolean timeProfile;
	private boolean firstTime = true;
	private String options;
	
	/** Returns a Plot of the selection mean gray value versus slice number. */
	public static Plot getPlot(ImagePlus imp) {
	    //	    IJ.register(MultiCh_ZAxisProfiler.class);
		return getPlot(imp, "time");
	}

	/** Returns a Plot of the selection mean versus slice number for the
		specified hyperstack, where 'options' can be "time" or "z-axis". */

	public static Plot getPlot(ImagePlus imp, String options) {
		MultiCh_ZAxisProfiler zap = new MultiCh_ZAxisProfiler();
		zap.imp = imp;
		zap.options = options;
		zap.isPlotMaker = true;
		Plot plot = zap.getPlot();
		return plot;
	}

	public void run(String arg) {
		imp = IJ.getImage();
		if (imp.getStackSize()<2) {
			IJ.error("ZAxisProfiler", "This command requires a stack.");
			return;
		}
		isPlotMaker = true;
		Plot plot = getPlot();
		if (plot!=null) {
			if (isPlotMaker)
				plot.setPlotMaker(this);
			plot.show();
		}
	}

	public Plot getPlot() {
	    if (!imp.isHyperStack()) {
			IJ.error("MultiCh ZAxisProfiler", "This command requires a hyperstack.");
			return null;
		}

		Roi roi = imp.getRoi();
		ImageProcessor ip = imp.getProcessor();
		double minThreshold = ip.getMinThreshold();
		double maxThreshold = ip.getMaxThreshold();
		imp.setC(1);
		float[] y = getHyperstackProfile(roi, minThreshold, maxThreshold);
		    if (y==null)
			return null;
		float[] x = new float[y.length];
		
		String xAxisLabel = showingDialog&&choice.equals(choices[0])?"Frame":"Slice";
		Calibration cal = imp.getCalibration();
		if (cal.scaled()) {
			double c = 1.0f;
			double origin = 0;
			if (timeProfile) {
				c = (float) cal.frameInterval;
				boolean zeroInterval = c==0;
				if (zeroInterval)
					c = 1;
				String timeUnit = zeroInterval?"Frame":"["+cal.getTimeUnit()+"]";
				xAxisLabel = timeUnit;
			} else {
				c = (float) cal.pixelDepth;
				boolean zeroDepth = c==0;
				if (zeroDepth)
					c = 1;
				origin = cal.zOrigin;
				String depthUnit = zeroDepth?"Slice":"["+cal.getZUnit()+"]";
				xAxisLabel = depthUnit;
			}
			for (int i=0; i<x.length; i++)
				x[i] = (float)((i-cal.zOrigin)*c);
		} else {
			for (int i=0; i<x.length; i++)
				x[i] = i+1;
		}
		String title;
		if (roi!=null) {
			Rectangle r = roi.getBounds();
			title = imp.getTitle()+"-"+r.x+"-"+r.y;
		} else
			title = imp.getTitle()+"-0-0";
		//String xAxisLabel = showingDialog&&choice.equals(choices[0])?"Frame":"Slice";
		Plot plot = new Plot(title, xAxisLabel, "Mean");
		double ymin = ProfilePlot.getFixedMin();
		double ymax = ProfilePlot.getFixedMax();
		if ((ymin==0.0 && ymax==0.0)) {
		    double[] b = Tools.getMinMax(y);
		    ymin = b[0];
		    ymax = b[1];
		}
		if (!(ymin==0.0 && ymax==0.0)) {
			double[] a = Tools.getMinMax(x);
			double xmin=a[0]; double xmax=a[1];
			plot.setLimits(xmin, xmax, ymin, ymax);
		}

		plot.setColor(Color.red);
		plot.addPoints(x, y, Plot.LINE);
		imp.setC(2);
		y = getHyperstackProfile(roi, minThreshold, maxThreshold);
		plot.setColor(Color.green);
		plot.addPoints(x, y, Plot.LINE);
		imp.setC(3);
		y = getHyperstackProfile(roi, minThreshold, maxThreshold);
		plot.setColor(Color.blue);
		plot.addPoints(x, y, Plot.LINE);
		plot.setColor(Color.black);


		if (!firstTime) {
			int pos = imp.getCurrentSlice();
			int size = imp.getStackSize();
			if (timeProfile) {
			    pos = imp.getT();
			    size = imp.getNFrames();
			} else {
			    pos = imp.getZ();
			    size = imp.getNSlices();
			}
			double xx = (pos-1.0)/(size-1.0);
			if (xx==0.0)
				plot.setLineWidth(2);
			plot.setColor(Color.blue);
			plot.drawNormalizedLine(xx, 0, xx, 1.0);
			plot.setColor(Color.black);
			plot.setLineWidth(1);
		}
		firstTime = false;

		return plot;


	}
	
	public ImagePlus getSourceImage() {
		return imp;
	}

	private float[] getHyperstackProfile(Roi roi, double minThreshold, double maxThreshold) {
		int slices = imp.getNSlices();
		int frames = imp.getNFrames();
		int c = imp.getC();
		int z = imp.getZ();
		int t = imp.getT();
		int size = slices;
		if (firstTime)
			timeProfile = slices==1 && frames>1;
		if (options==null && slices>1 && frames>1 && (!isPlotMaker ||firstTime)) {
			showingDialog = true;
			GenericDialog gd = new GenericDialog("Profiler");
			gd.addChoice("Profile", choices, choice);
			gd.showDialog();
			if (gd.wasCanceled())
				return null;
			choice = gd.getNextChoice();
			timeProfile = choice.equals(choices[0]);
		}
		if (options!=null)
			timeProfile = frames>1 && !options.contains("z");
		if (timeProfile)
			size = frames;
		else
			size = slices;
		float[] values = new float[size];
		Calibration cal = imp.getCalibration();
		Analyzer analyzer = new Analyzer(imp);
		int measurements = Analyzer.getMeasurements();
		boolean showResults = !isPlotMaker && measurements!=0 && measurements!=LIMIT;
		measurements |= MEAN;
		if (showResults) {
			if (!Analyzer.resetCounter())
				return null;
		}
		ImageStack stack = imp.getStack();
		boolean showProgress = size>400 || stack.isVirtual();
		for (int i=1; i<=size; i++) {
			if (showProgress)
				IJ.showProgress(i,size);
			int index = 1;
			if (timeProfile)
				index = imp.getStackIndex(c, z, i);
			else
				index = imp.getStackIndex(c, i, t);
			ImageProcessor ip = stack.getProcessor(index);
			if (minThreshold!=ImageProcessor.NO_THRESHOLD)
				ip.setThreshold(minThreshold,maxThreshold,ImageProcessor.NO_LUT_UPDATE);
			ip.setRoi(roi);
			ImageStatistics stats = ImageStatistics.getStatistics(ip, measurements, cal);
			analyzer.saveResults(stats, roi);
			values[i-1] = (float)stats.mean;
		}
		if (showResults) {
			ResultsTable rt = Analyzer.getResultsTable();
			rt.show("Results");
		}
		return values;
	}
}

 
