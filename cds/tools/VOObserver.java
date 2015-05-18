package cds.tools;

public abstract interface VOObserver {
	/** The observer method to call on a position VO event
	 * in order to transmit the current coordinates J2000 position.
	 * Generally called by a JAVA mouse event, it is strongly recommended
	 * to implemented this method as short as possible.
	 * @param raJ2000 Right ascension in degrees J2000 
	 * @param deJ2000 Declination in degrees J2000
	 */
	public abstract void position(double raJ2000,double deJ2000);
	
	}		

	