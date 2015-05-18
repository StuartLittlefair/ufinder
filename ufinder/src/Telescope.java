package ufinder;

// Simple class to store inforamtion on telescopes.
//
// 'name'      is the case sensitive name to be used for the telescope
// 'zeroPoint' is an array of mags giving 1 counts/sec at airmass zero for ugriz
// plateScale  is the scale in arcsec/pixel
// application is the name of an XML application to be used for this telescope
// flipped     is a flag to store if the north-east axis flipped or not?
// delta_pa    is the rotator position when the ultracam chip runs north-south
// delta_x     is the error in alignment (x-direction) between chip centre and telescope pointing (arcsecs)
// delta_y     is the error in alignment (y-direction) between chip centre and telescope pointing (arcsecs)

public class Telescope {

    public Telescope(String name, double[] zeroPoint, double plateScale, boolean flipped,
    		double delta_pa, double delta_x, double delta_y, String application) {
	this.name        = name;
	this.zeroPoint   = zeroPoint;
	this.plateScale  = plateScale;
	this.flipped     = flipped;
	this.delta_pa    = delta_pa;
	this.delta_x     = delta_x;
	this.delta_y     = delta_y;
	this.application = application;
    }

    public String name;
    public double[] zeroPoint;
    public double plateScale;
    public String application;
    public boolean flipped; 
    public double delta_pa; 
    public double delta_x; 
    public double delta_y;


};
