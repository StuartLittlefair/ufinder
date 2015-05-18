package ufinder;

import java.lang.Math;
import java.awt.Color;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.NumberFormat;
import javax.swing.Action;
import javax.swing.AbstractAction;
import javax.swing.JTextField;
import javax.swing.Timer;

/** Subclass of JTextField for range-checked integer input. Supports
 * up and down arrows and left and right mouse buttons to increment 
 * and decrement the value.  Focus-follows-mouse behaviour.
 */

public class IntegerTextField extends JTextField {

    // The time between updates of the field when the mouse is held down, millisecs
    private static final int DELAY = 20;

    // The time between updates at the start to allow user to add one
    private static final int INITIAL_DELAY = 400;    

    private int    vmin;
    private int    vmax;
    private int    increment;
    private String name;
    private Color  normalColour;
    private Color  errorColour;

    // Increment/decrement timer
    private Timer  incrementTimer = null;
    private int[]  specialValue = null;

    // check for autorepeat
    private class ListenForKeyPress implements Runnable {

	public void run(){

	    // is there going to be a keypress next?
	    java.awt.AWTEvent nextPress = null;
	    try{
		Thread.sleep(0);
		while(nextPress == null ){ // loop until we recieve the next queue event
		    nextPress = getToolkit().getSystemEventQueue().peekEvent();
		}
		if(nextPress.getID() != 401) { // no keypress followed.
		    //timerOff();
		    fireActionPerformed(); 
		}
	    } catch (InterruptedException e) {
	    }

	}
    }


    // formatting for integers to have correct number of digits
    NumberFormat fmt = NumberFormat.getIntegerInstance();


    /** Constructor with an intial value, a range and an indicator of whether the field
     * is enable.
     * @param value        the initial value to show in the field
     * @param vmin         the minimum possible value
     * @param vmax         the maximum possible value
     * @param increment    the step by which to increment when using the up/down arrows
     * @param name         name for the field for when reporting errors
     * @param enabled      is the field active or not
     * @param normalColour the normal colour for the field
     * @param errorColour  the colour when the field is in error
     * @param width        the field width
     *@param minwidth   the minimum number digits to display
     */
    public IntegerTextField(int value, int vmin, int vmax, int increment, String name, boolean enabled, 
			    Color normalColour, Color errorColour, int width, int minwidth) {
	
	super(width);
	fmt.setMinimumIntegerDigits(minwidth);
	fmt.setGroupingUsed(false);
	this.setText(fmt.format(value));
	this.setTransferHandler(null);
	this.normalColour = normalColour;
	this.errorColour  = errorColour;
	this.setNormal();
	this.vmin      = vmin;
	this.vmax      = vmax;
	this.increment = increment;
	this.name      = name;
	this.setEnabled(enabled);

	// Now we define the response to keyboard input	
	this.addKeyListener(
			    new KeyAdapter(){
				
				/** Defines response to printing characters. Basically
				 * ignores any but integers and -
				 */
				public void keyTyped(KeyEvent event){

				    char c = event.getKeyChar();
				    
				    // Only allowed characters are 0-9 and '-' (if -ve integers alllowed)
				    if (!Character.isDigit(c) && (IntegerTextField.this.vmin >= 0 || c != '-')){
					getToolkit().beep();
					event.consume();
				    }

				}

				// fires an event when the key is released. 
				public void keyReleased(KeyEvent event){
				    Thread t = new Thread(new ListenForKeyPress());
				    t.start();
				    while(t.isAlive()){ // if thread runs for more than 'patience' milliseconds then abort
					try{
					    t.join(1);
					    if ( t.isAlive()) {						 
						t.interrupt();
						t.join();
					    }
					}catch (InterruptedException e){
					}
				    }

				}

				/** Defines what happens when various non-printing keys are pressed.
				 * The up and down arrows increment and decrement the field value.
				 * The page up & down jump up and down by large amounts.
				 */				
				public void keyPressed(KeyEvent event){
				    
				    int n;
				    
				    if(event.getKeyCode() == java.awt.event.KeyEvent.VK_UP){
					
					// Increment
					if((event.getModifiersEx() & (InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK) ) == (InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK) ) {
					    addIncrement(50);
					}else if((event.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) == InputEvent.SHIFT_DOWN_MASK) {
					    addIncrement(10);
					}else{
					    addIncrement(1);
					}
					
				    }else if(event.getKeyCode() == java.awt.event.KeyEvent.VK_DOWN){
					
					// Decrement					    
					if((event.getModifiersEx() & (InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK) ) == (InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK) ) {
					    subIncrement(50);
					}else if((event.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) == InputEvent.SHIFT_DOWN_MASK){
					    subIncrement(10);
					}else{
					    subIncrement(1);
					}

				    }else if(event.getKeyCode() == java.awt.event.KeyEvent.VK_PAGE_DOWN){
					
					// Set to minimum value
					setText(fmt.format(IntegerTextField.this.vmin));
					setNormal();
					fireActionPerformed();
				    }else if(event.getKeyCode() == java.awt.event.KeyEvent.VK_PAGE_UP){
					
					// Set to maximum value					    
					setText(fmt.format(IntegerTextField.this.vmax));
					setNormal();
					fireActionPerformed();
				    }else if(event.getKeyCode() == java.awt.event.KeyEvent.VK_DELETE ||
					     event.getKeyCode() == java.awt.event.KeyEvent.VK_BACK_SPACE){
					
					// Delete before cursor position
					int m = getCaretPosition();
					if(m > 0){
					    String text = getText();
					    if(m > 1)
						setText(text.substring(0,m-1) + text.substring(m));
					    else
						setText(text.substring(1));
					    setCaretPosition(m-1);
					}

				    }
				}
			    });

	// Mouse behaviour. Nibbles cheese. Left button increments, right-button decrements. Timers are used
	// to allow automatic updates if buttons are held down. Shift & alt are used as accelerators
	this.addMouseListener(new MouseAdapter() {

		// Set the focus as soon as the mouse is on this field, and 
		// set the caret to the end of it
		public void mouseEntered(MouseEvent event) {
		    if(isEnabled()){
			// Request the focus (if don't already have it), 
			if(!hasFocus()) { 
			    requestFocus(); 
			    setCaretPosition(getText().length());
			}
		    }
		}

		// Increment and decrement with the first and third mouse buttons
		public void mousePressed(MouseEvent event) {

		    if(isEnabled()){
			int n;
			if ((event.getModifiersEx() & (InputEvent.BUTTON1_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK) ) == 
			    (InputEvent.BUTTON1_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK)) {
			    
			    addIncrement(50);
			    fireActionPerformed();
			    // Initialise timer
			    ActionListener taskPerformer = new ActionListener() {
				    public void actionPerformed(ActionEvent event) {
					addIncrement(50);
				    }
				};	
			    
			    timerOff();
			    incrementTimer = new Timer(DELAY, taskPerformer);	
			    incrementTimer.setInitialDelay(INITIAL_DELAY);
			    incrementTimer.start();
			    
			    
			}else if ((event.getModifiersEx() & (InputEvent.BUTTON1_DOWN_MASK | InputEvent.CTRL_DOWN_MASK) ) == (InputEvent.BUTTON1_DOWN_MASK | InputEvent.CTRL_DOWN_MASK)) {
			    
			    upToNext();
			    fireActionPerformed();
			}else if ((event.getModifiersEx() & (InputEvent.BUTTON1_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK) ) == (InputEvent.BUTTON1_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK)) {
			    
			    addIncrement(10);
			    fireActionPerformed();
			    // Initialise timer
			    ActionListener taskPerformer = new ActionListener() {
				    public void actionPerformed(ActionEvent event) {
					addIncrement(10);
				    }
				};	
			    
			    timerOff();
			    incrementTimer = new Timer(DELAY, taskPerformer);	
			    incrementTimer.setInitialDelay(INITIAL_DELAY);
			    incrementTimer.start();
			    
			}else if ((event.getModifiersEx() & InputEvent.BUTTON1_DOWN_MASK) == InputEvent.BUTTON1_DOWN_MASK) {
			    
			    addIncrement(1);
			    fireActionPerformed();
			    // Initialise timer
			    ActionListener taskPerformer = new ActionListener() {
				    public void actionPerformed(ActionEvent event) {
					addIncrement(1);
				    }
				};	
			    
			    timerOff();
			    incrementTimer = new Timer(DELAY, taskPerformer);	
			    incrementTimer.setInitialDelay(INITIAL_DELAY);
			    incrementTimer.start();
			    
			}else if ((event.getModifiersEx() & (InputEvent.BUTTON3_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK) ) == 
				  (InputEvent.BUTTON3_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK)) {
			    
			    subIncrement(50);
			    fireActionPerformed();
			    // Initialise timer
			    ActionListener taskPerformer = new ActionListener() {
				    public void actionPerformed(ActionEvent event) {
					subIncrement(50);
				    }
				};	
			    
			    timerOff();
			    incrementTimer = new Timer(DELAY, taskPerformer);
			    incrementTimer.setInitialDelay(INITIAL_DELAY);
			    incrementTimer.start();
			    
			}else if ((event.getModifiersEx() & (InputEvent.BUTTON3_DOWN_MASK | InputEvent.CTRL_DOWN_MASK) ) == (InputEvent.BUTTON3_DOWN_MASK | InputEvent.CTRL_DOWN_MASK)) {
			    
			    downToNext();
			    fireActionPerformed();
			}else if ((event.getModifiersEx() & (InputEvent.BUTTON3_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK) ) == (InputEvent.BUTTON3_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK)) {

			    subIncrement(10);
			    fireActionPerformed();
			    // Initialise timer
			    ActionListener taskPerformer = new ActionListener() {
				    public void actionPerformed(ActionEvent event) {
					subIncrement(10);
				    }
				};	
			    
			    timerOff();
			    incrementTimer = new Timer(DELAY, taskPerformer);
			    incrementTimer.setInitialDelay(INITIAL_DELAY);
			    incrementTimer.start();
			    
			}else if ((event.getModifiersEx() & InputEvent.BUTTON3_DOWN_MASK) == InputEvent.BUTTON3_DOWN_MASK) {
			    
			    subIncrement(1);
			    fireActionPerformed();
			    // Initialise timer
			    ActionListener taskPerformer = new ActionListener() {
				    public void actionPerformed(ActionEvent event) {
					subIncrement(1);
				    }
				};	
			    
			    timerOff();
			    incrementTimer = new Timer(DELAY, taskPerformer);
			    incrementTimer.setInitialDelay(INITIAL_DELAY);
			    incrementTimer.start();
			    
			}else if ((event.getModifiersEx() & InputEvent.BUTTON2_DOWN_MASK) == InputEvent.BUTTON2_DOWN_MASK) {
			    
			    // Print out information about the field
			    System.out.println("IntegerTextField, name = \"" + IntegerTextField.this.name + "\", range = " +
					       IntegerTextField.this.vmin + " to " + IntegerTextField.this.vmax + ", increment = " +
					       IntegerTextField.this.increment);
			    if(IntegerTextField.this.specialValue == null){
				System.out.println("There are no special values associated with this field");
			    }else{
				System.out.print("Special values:");
				for(int i=0; i<IntegerTextField.this.specialValue.length-1; i++)
				    System.out.print(" " + IntegerTextField.this.specialValue[i] + ",");
				System.out.println(" " + IntegerTextField.this.specialValue[IntegerTextField.this.specialValue.length-1]);
			    }
			}
		    }
		}
		    
		// Switch off timer if button is released
		public void mouseReleased(MouseEvent event) {
		    timerOff();
		    fireActionPerformed();
		}
		    
		// or if mouse is moved off field
		public void mouseExited(MouseEvent event) {
		    timerOff();
		}
		    

	    });

    }
	
    // Switches off timer
    private void timerOff(){
	if(incrementTimer != null)
	    incrementTimer.stop();
    }
    
    // Set special values which must ascend monotonically
    public void setSpecial(int[] special){
	specialValue = special;
    }

    /** Sets the current value, truncating at minimum and maximum values
     * @param value the new value to set
     */
    public void setValue(int value){
	if(value < this.vmin){
	    value = this.vmin;
	}else if(value > this.vmax){
	    value = this.vmax;
	}
	this.setText(fmt.format(value));
	this.setNormal();
    }

    /** Sets the minimum limit. It is not allowed to exceed the current maximum,
     * and if the value goes out of range, the field is set to the error colour
     * @param vmax the new maximum value to set
     */
    public void setVmin(int vmin){
	if(vmin > this.vmax){
	    this.vmin = this.vmax;
	}else{
	    this.vmin = vmin;
	}
    }

    /** Sets the maximum limit, truncating it at the current minimum
     * and altering the displayed value if need be
     * @param vmax the new maximum value to set
     */
    public void setVmax(int vmax){
	if(vmax < this.vmin){
	    this.vmax = this.vmin;
	}else{
	    this.vmax = vmax;
	}
    }

    /** Sets the increment. Must be greater than or equal to 1.
     * @param increment the new value to increment by
     */
    public void setIncrement(int increment){
	if(increment < 1)
	    this.increment = 1;
	else
	    this.increment = increment;
    }

    /** Gets the current value, throwing an exception if a problem
     * is encountered
     * @param value the new value to set
     */
    public int getValue() throws Exception {
	try{
	    int n = Integer.parseInt(this.getText());
	    if(n < vmin || n > vmax)
		throw new OutOfRangeException(name + " is out of range " + vmin + " to " + vmax); 
	    this.setNormal();
	    return n;
	}
	catch(Exception e){
	    //	    System.out.println(e.toString());
	    this.setError();
	    throw e;
	}
    }

    /** Checks that the current field contains a valid value */
    public boolean checkValue() {
	try{
	    int n = Integer.parseInt(this.getText());
	    if(n < vmin || n > vmax)
		throw new OutOfRangeException(name + " is out of range " + vmin + " to " + vmax); 
	    this.setNormal();
	    return true;
	}
	catch(Exception e){
	    this.setError();
	    System.out.println(e);
	    return false;
	}
    }

    // Increment the text field by nmult times the standard increment
    public void addIncrement(int nmult){
	try {
	    int n = Integer.parseInt(getText());
	    if(n + nmult*increment > vmax) {
		n  = vmax;
	    }else if(n + nmult*increment < vmin) {
		n  = vmin;
	    }else{
		n += nmult*increment;
		n  = increment * (int)( n / (double)increment + 0.5);
	    }
	    setNormal();
	    setText(fmt.format(n));
	}
	catch(Exception e){
	    System.out.println(e.toString());
	    System.out.println("Failed to increment field = " + name);
	    setError();
	}					
    }

    // Tries to jump to next higher specialValue if there is one
    public void upToNext(){
	try {
	    int n = Integer.parseInt(getText());
	    if(specialValue != null){
		for(int i=0; i<specialValue.length; i++){
		    if(n < specialValue[i] && specialValue[i] % increment == 0){
			setNormal();
			setText(fmt.format(specialValue[i]));
			return;
		    }
		}
		System.out.println("There is no special value set higher than the current value of \"" + name + "\" that is compatible with the increment = " + increment);
	    }else{
		System.out.println("There are no special values for " + name);
	    }
	}
	catch(Exception e){
	    System.out.println(e.toString());
	    System.out.println("Failed to jump to next higher special value = " + name);
	    setError();
	}					
    }

    // Decrement the text field
    public void subIncrement(int nmult){
	try {
	    int n = Integer.parseInt(getText());
	    if(n - nmult*increment > vmax) {
		n  = vmax;
	    }else if(n - nmult*increment < vmin) {
		n  = vmin;
	    }else{
		n -= nmult*increment;
	    }
	    setNormal();
	    setText(fmt.format(n));
	}
	catch(Exception e){
	    System.out.println(e.toString());
	    System.out.println("Failed to decrement field = " + name);
	    setError();
	}					
    }

    // Tries to jump to next lower specialValue if there is one
    public void downToNext(){
	try {
	    int n = Integer.parseInt(getText());
	    if(specialValue != null){
		for(int i=specialValue.length; i>0; i--){
		    if(n > specialValue[i-1] && specialValue[i-1] % increment == 0){
			setNormal();
			setText(fmt.format(specialValue[i-1]));
			return;
		    }
		}
		System.out.println("There is no special value set lower than the current value of \"" + name + "\" that is compatible with the increment = " + increment);
	    }else{
		System.out.println("There are no special values for " + name);
	    }
	}
	catch(Exception e){
	    System.out.println(e.toString());
	    System.out.println("Failed to jump to next lower special value = " + name);
	    setError();
	}					
    }


    public void setNormal() {
	this.setBackground(normalColour);
    }

    public void setError() {
	this.setBackground(errorColour);
    }

    /** Exception class to report problems */
    public static class OutOfRangeException extends Exception {
	public OutOfRangeException(String s)
	{
	    super(s);
	}
    }

}
