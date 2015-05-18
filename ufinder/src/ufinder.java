package ufinder;

import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.text.DecimalFormat;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.text.NumberFormatter;
import java.beans.*;
import java.net.URL;
import cds.tools.VOApp;
import cds.tools.VOObserver;

import java.awt.geom.Ellipse2D;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.FontMetrics;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

public class ufinder extends JFrame implements VOApp,
                                               ActionListener{
 
    // Telescope data. See the class for a full description of the fields
    private static final Telescope[] TELESCOPE_DATA = {
	// NTT ZPs scaled from WHT using accurate collecting areas accounting for obstruction & 0.9 reflectivity of M3
    	new Telescope("NTT", new double[] {24.62, 26.43, 25.77, 25.63, 24.79}, 0.35, true, 0.0, 0.0, 0.0, "vlt.xml"),
	// accurate - assume 0.85 reflectivity for M3 
	// new Telescope("NTT", new double[] {24.56, 26.37, 25.71, 25.57, 24.73}, 0.35, true, 0.0, 0.0, 0.0, "vlt.xml"),
	// less accurate - 0.9 new Telescope("NTT", new double[] {24.84, 26.45, 25.79, 25.65, 24.81}, 0.35, true, 0.0, 0.0, 0.0, "vlt.xml"),
	new Telescope("VLT", new double[] {26.54, 28.35, 27.69, 27.55, 26.71}, 0.1557, true, 0.0, -1.4402, -3.1856, "vlt.xml"),
    	new Telescope("WHT", new double[] {25.11, 26.92, 26.26, 26.12, 25.28}, 0.30, false, 0.0, 0.0, 0.0, "wht.xml"),
    };
    /** delta_pa is zero on the vlt because of a software offset applied by the TCS. The 
     * physical value is -84.7 degrees
     */
    // The following is used to pass the telescope data around
    private Telescope _telescope = null, _old_telescope = null;
    
    //------------------------------------------------------------------------------------------
    // Sky parameters
    // Extinction, mags per unit airmass
    private static final double[] EXTINCTION = {0.50, 0.19, 0.09, 0.05, 0.04};
    
    // Sky brightness, mags/arcsec**2, dark, grey, bright, in ugriz
    private static final double[][] SKY_BRIGHT = {
	{22.4, 22.2, 21.4, 20.7, 20.3},
	{21.4, 21.2, 20.4, 20.1, 19.9},
	{18.4, 18.2, 17.4, 17.9, 18.3}
    };
    
    //------------------------------------------------------------------------------------------
    // Instrument parameters
    
    // Readout parameters
    private static final double   GAIN_TURBO = 1.5;           // electrons per count
    private static final double   GAIN_FAST  = 1.4;           // electrons per count
    private static final double   GAIN_SLOW  = 1.3;           // electrons per count
    
    // Readout noise for 1x1, 2x2, 4x4, 8x8
    // EDIT: READ NOISE FOR TURBO VERY APPROX
    private static final double[] READ_NOISE_TURBO = {7.0, 7.0, 7.0, 7.0};
    private static final double[] READ_NOISE_FAST  = {4.9, 4.9, 5.1, 6.4};
    private static final double[] READ_NOISE_SLOW  = {3.6, 3.6, 4.0, 5.4};
    
    // Dark count rate, counts/sec/pixel
    private static final double DARK_COUNT = 0.1;
    
    // Timing parameters from Vik
    public static final double INVERSION_DELAY = 110.;   // microseconds
    public static final double VCLOCK_FRAME    = 23.3;   // microseconds
    public static final double VCLOCK_STORAGE  = 23.3;   // microseconds
    public static final double HCLOCK          = 0.48;   // microseconds
    public static final double CDS_TIME_FDD    = 1.84;    // microseconds
    public static final double CDS_TIME_FBB    = 4.4;    // microseconds
    public static final double CDS_TIME_CDD    = 10.;    // microseconds
    public static final double SWITCH_TIME     = 1.2;    // microseconds
    
    // Special values of NY when pipe shift hits a minimum
    public static final int[] specialNy = {8, 10, 13, 18, 21, 24, 31, 38, 41, 49, 54, 60, 68, 79, 93, 114, 147, 206, 344};
    
    // Colours
    public static final Color DEFAULT_COLOUR    = new Color(220, 220, 255);
    public static final Color SEPARATOR_BACK    = new Color(100, 100, 100);
    public static final Color SEPARATOR_FORE    = new Color(150, 150, 200);
    public static final Color LOG_COLOUR        = new Color(240, 230, 255);
    public static final Color ERROR_COLOUR      = new Color(255, 0,   0  );
    public static final Color WARNING_COLOUR    = new Color(255, 100, 0  );
    public static final Color GO_COLOUR         = new Color(0,   255, 0  );
    public static final Color STOP_COLOUR       = new Color(255, 0,   0  );
    
    // Font
    public static final Font DEFAULT_FONT = new Font("Dialog", Font.BOLD, 12);
    
    // Width for horizontal separator
    public static final int SEPARATOR_WIDTH = 5;
    
    // Recognised by the method 'speed'
    public static final int DETAILED_TIMING = 1;
    public static final int TIMING_UPDATE   = 2;
    public static final int CYCLE_TIME_ONLY = 3;
    private boolean _validStatus = true; 
    private boolean _magInfo     = true;
    
    private int _filterIndex    = 1;
    private int _skyBrightIndex = 1;
    
    // Configuration file
    public String CONFIG_FILE = System.getProperty("CONFIG_FILE","ufinder.conf");
    
    // Configurable values
    public static boolean RTPLOT_SERVER_ON;
    public static boolean ULTRACAM_SERVERS_ON;
    public static boolean OBSERVING_MODE;
    public static boolean DEBUG;
    public static boolean FILE_LOGGING_ON;
    public static String  TELESCOPE             = null;
    public static String  HTTP_CAMERA_SERVER    = null;
    public static String  HTTP_DATA_SERVER      = null;
    public static String  HTTP_PATH_GET         = null;
    public static String  HTTP_PATH_EXEC        = null;
    public static String  HTTP_PATH_CONFIG      = null;
    public static String  HTTP_SEARCH_ATTR_NAME = null;
    
    public static String  APP_DIRECTORY         = null;
    public static boolean XML_TREE_VIEW;
    public static boolean TEMPLATE_FROM_SERVER;
    public static String  TEMPLATE_DIRECTORY    = null;
    public static boolean EXPERT_MODE;
    public static String  LOG_FILE_DIRECTORY    = null;
    public static boolean CONFIRM_ON_CHANGE;
    public static boolean CHECK_FOR_MASK;
    
    public static String[] TEMPLATE_LABEL       = null;
    public static String[] TEMPLATE_PAIR        = null;
    public static String[] TEMPLATE_APP         = null;
    public static String[] TEMPLATE_ID          = null;
    public static String   POWER_ON             = null;
    public static String   POWER_OFF            = null;
    
    // Binning factors
    private int xbin    = 1;
    private int ybin    = 1;
    private final IntegerTextField xbinText = new IntegerTextField(xbin, 1, 8, 1, "X bin factor", true, DEFAULT_COLOUR, ERROR_COLOUR, 2,1);
    private final IntegerTextField ybinText = new IntegerTextField(ybin, 1, 8, 1, "Y bin factor", true, DEFAULT_COLOUR, ERROR_COLOUR, 2,1);
    
    private static JComboBox templateChoice;
    public int numEnable;
    
    private String applicationTemplate    = "Fullframe + clear";
    private String oldApplicationTemplate = "Fullframe + clear";
    
    // Readout speeds
    private static final String[] SPEED_LABELS = {
    "Turbo",
	"Fast",
	"Slow"
    };
    private JComboBox speedChoice = new JComboBox(SPEED_LABELS);
    private String readSpeed = "Slow";
    
    private static final String SLOW_SPEED = "0xcdd";
    private static final String FAST_SPEED = "0xfbb";
	private static final String TURBO_SPEED = "0xfdd";

    // Exposure delay measured in 0.1 millisecond intervals, so prompted
    // for in terms of millseconds plus a small text field of 0.1 milliseconds
    // that is only enabled in expert mode as it comes with some dangers.
    private int expose = 5;
    private final IntegerTextField exposeText     = new IntegerTextField(0, 0, 100000, 1, "exposure, milliseconds", true, DEFAULT_COLOUR, ERROR_COLOUR, 6,1);
    private final IntegerTextField tinyExposeText = new IntegerTextField(5, 0, 9, 1, "exposure increment, 0.1 milliseconds", true, DEFAULT_COLOUR, ERROR_COLOUR, 2,1);
    private int numExpose = -1;
    private final IntegerTextField numExposeText = new IntegerTextField(numExpose, -1, 100000, 1, "Number of exposures", true, DEFAULT_COLOUR, ERROR_COLOUR, 6,1);
    
    private static final JLabel windowsLabel = new JLabel("Windows");
    private static final JLabel ystartLabel  = new JLabel("ystart");
    private static final JLabel xleftLabel   = new JLabel("xleft");
    private static final JLabel xrightLabel  = new JLabel("xright");
    private static final JLabel nxLabel      = new JLabel("nx");
    private static final JLabel nyLabel      = new JLabel("ny");
    
    // Fields for user information
    private static final JTextField _objectText     = new JTextField("", 15);
    private static final JTextField _filterText     = new JTextField("", 15);
    private static final JTextField _progidText     = new JTextField("", 15);
    private static final JTextField _piText         = new JTextField("", 15);
    private static final JTextField _observerText   = new JTextField("", 15);
    
    // Fields for signal-to-noise estimates
    private static final DoubleTextField _magnitudeText  = new DoubleTextField(18.0, 5.,  35., 0.1,  "Target magnitude",    true, DEFAULT_COLOUR, ERROR_COLOUR, 6);
    private static final DoubleTextField _seeingText     = new DoubleTextField( 1.0, 0.2, 20., 0.1,  "Seeing, FWHM arcsec", true, DEFAULT_COLOUR, ERROR_COLOUR, 6);
    private static final DoubleTextField _airmassText    = new DoubleTextField(1.5, 1.0, 5.0,  0.05, "Airmass", true, DEFAULT_COLOUR, ERROR_COLOUR, 6);
    
    private static WindowPairs _windowPairs;
    
    // Use this a fair bit, so just make one
    private static final GridBagLayout gbLayout = new GridBagLayout();
    
    // To switch between setup & observing panels
    JTabbedPane _actionPanel = null;
    JPanel _expertSetupPanel = null;
    JPanel _noddySetupPanel  = null;
    
    // Action buttons associated with ULTRACAM servers
    private static final JButton syncWindows      = new JButton("Sync wins");

    // Timing info fields
    private final JTextField _frameRate        = new JTextField("", 7);
    private final JTextField _cycleTime        = new JTextField("", 7);
    private final JTextField _dutyCycle        = new JTextField("", 7);
    private final JTextField _totalCounts      = new JTextField("", 7);
    private final JTextField _peakCounts       = new JTextField("", 7);
    private final JTextField _signalToNoise    = new JTextField("", 7);
    private final JTextField _signalToNoiseOne = new JTextField("", 7);
    private final JTextArea displayArea = new JTextArea();

    // Object for manipulating the Ultracam Field of View
    private static final FOVmanip FOV = new FOVmanip();
    
    // the surveys that this tool can query
    private JComboBox surveyChoice;
    public static String[] SURVEY_LABEL = {"DSS2-BLUE", "DSS2-RED", "DSS1", "Aladin"};
    // string that stores the choice
    private String surveyString="DSS2-BLUE";
    
    private final JTextField objText = new JTextField("",10);
    private final JTextField coordText = new JTextField("",10);
    private static final JButton aladinGo = new JButton("Launch Aladin");
    private static final JButton addTarg = new JButton("Sel Targ");
    private static final JButton addComp = new JButton("Add Comp");
    
    int raHour=0, raMin=0, decDeg=0, decMin=0, paDeg=0;
    double raSec=0.0, decSec=0.0;
    IntegerTextField raHourVal = new IntegerTextField(raHour,  0,23,1,"RA hours",    true, DEFAULT_COLOUR, ERROR_COLOUR, 2,2);
    IntegerTextField raMinVal  = new IntegerTextField(raMin ,  0,59,1,"RA minutes",  true, DEFAULT_COLOUR, ERROR_COLOUR, 2,2);
    IntegerTextField decDegVal = new IntegerTextField(decDeg,-90,90,1,"Dec degrees", true, DEFAULT_COLOUR, ERROR_COLOUR, 2,2);
    IntegerTextField decMinVal = new IntegerTextField(decMin,  0,59,1,"Dec minutes", true, DEFAULT_COLOUR, ERROR_COLOUR, 2,2);
    DoubleTextField  raSecVal  = new DoubleTextField(raSec,0.0,59.99,0.1,"RA seconds", true, DEFAULT_COLOUR, ERROR_COLOUR, 5); 
    DoubleTextField  decSecVal = new DoubleTextField(decSec,0.0,59.99,0.1,"Dec seconds", true, DEFAULT_COLOUR, ERROR_COLOUR, 5); 
    DoubleTextField  paDegVal = new DoubleTextField(paDeg,  0.0,359.9,0.3,"Position Angle",    true, DEFAULT_COLOUR, ERROR_COLOUR, 5);

    // handle action performed events
    public void actionPerformed(ActionEvent e){
    	FOVSync();
    }

    private  class aladinInstance implements Runnable {
    	public void run() {
    	    mw.startAladin();
    	}
    }

    private VOApp aladin = null;
    String aladinTarget=null;

    private static ufinder mw = null;

    public void FOVSync () {
	// update FOV in Aladin
	
    	FOV.configMainWin(_telescope);
	for(int i=0; i<numEnable; i++){
	    FOV.addWindowPair(_windowPairs, i, _telescope);
	    FOV.configWindowPair(_windowPairs, i, _telescope);
	}
	for(int i=numEnable; i<3; i++)
	    FOV.delWindowPair(i);				
	String raText=null, decText=null;
    	try {
	    raText = raHourVal.getText() + ":" + raMinVal.getText() + ":" + String.valueOf(raSecVal.getValue());
	    decText = decDegVal.getText() + ":" + decMinVal.getText() + ":" + decSecVal.getText();
	    FOV.setCentre(raText, decText);
	    FOV.setPA(String.valueOf(paDegVal.getValue()), _telescope);
	} catch (Exception e) {
	    // TODO Auto-generated catch block
	    e.printStackTrace();
	}
	
	if (aladin != null){
	    displayArea.append(FOV.getText());
	    InputStream in;	
	    in = FOV.getStream();
	    aladin.execCommand("rm 'UCAM_FoV'");
	    aladin.putVOTable(mw,in,"UCAM_FoV");
	}    	
    }
    
    /** This is the constructor which sets up the GUI, adds the panels etc
     * 
     *
     */    	
    public ufinder () {
	
    	try{
	    
	    // debugging panel to display aladin commands to ufinder
		
	    JFrame cmdFrame = new JFrame("Aladin Commands");
	    cmdFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	    JPanel newPan = new JPanel(new BorderLayout());
	    displayArea.setEditable(false);
	    JScrollPane scrollPane = new JScrollPane(displayArea);
	    scrollPane.setPreferredSize(new Dimension(375, 125));
	    newPan.add(scrollPane,BorderLayout.CENTER);
	    cmdFrame.setContentPane(newPan);
	    //Display the window.
	    cmdFrame.pack();
	    //cmdFrame.setVisible(true);
	    

	    // Build GUI - set colours and fonts
	    UIManager.put("OptionPane.background",         DEFAULT_COLOUR);
	    UIManager.put("Panel.background",              DEFAULT_COLOUR);
	    UIManager.put("Button.background",             DEFAULT_COLOUR);
	    UIManager.put("CheckBoxMenuItem.background",   DEFAULT_COLOUR);
	    UIManager.put("SplitPane.background",          DEFAULT_COLOUR);
	    UIManager.put("Table.background",              DEFAULT_COLOUR);
	    UIManager.put("Menu.background",               DEFAULT_COLOUR);
	    UIManager.put("MenuItem.background",           DEFAULT_COLOUR);
	    UIManager.put("TextField.background",          DEFAULT_COLOUR);
	    UIManager.put("ComboBox.background",           DEFAULT_COLOUR);
	    UIManager.put("TabbedPane.background",         DEFAULT_COLOUR);
	    UIManager.put("TabbedPane.selected",           DEFAULT_COLOUR);
	    UIManager.put("MenuBar.background",            DEFAULT_COLOUR);
	    UIManager.put("window.background",             DEFAULT_COLOUR); 
	    UIManager.put("Slider.background",             DEFAULT_COLOUR);
	    UIManager.put("TextPane.background",           LOG_COLOUR);
	    UIManager.put("Tree.background",               LOG_COLOUR);
	    UIManager.put("RadioButtonMenuItem.background",DEFAULT_COLOUR);
	    UIManager.put("RadioButton.background",        DEFAULT_COLOUR);
	    UIManager.put("Table.font",                    DEFAULT_FONT);
	    UIManager.put("TabbedPane.font",               DEFAULT_FONT);
	    UIManager.put("OptionPane.font",               DEFAULT_FONT);
	    UIManager.put("Menu.font",                     DEFAULT_FONT);
	    UIManager.put("MenuItem.font",                 DEFAULT_FONT);
	    UIManager.put("Button.font",                   DEFAULT_FONT);
	    UIManager.put("ComboBox.font",                 DEFAULT_FONT);
	    UIManager.put("RadioButtonMenuItem.font",      DEFAULT_FONT);
	    UIManager.put("RadioButton.font",              DEFAULT_FONT);

	    // Load configuration file
	    loadConfig();
	
	    // This is a JFrame of sorts. Let's add titles etc
	    this.setTitle("Ultracam finding chart and acquisition tool");
	    this.setSize(800,400);
	    
	    
	    final Container container = this.getContentPane();
	    container.setBackground(DEFAULT_COLOUR);
	    container.setLayout(gbLayout);
	    
	    // Menu bar
	    final JMenuBar menubar = new JMenuBar();
	    this.setJMenuBar(menubar);	    
	    // File menu
	    menubar.add(createFileMenu());
	    
	    // Middle-left panel for displaying target and s-to-n information
	    addComponent( container, createTimingPanel(),  0, 2,  1, 1, GridBagConstraints.NONE, GridBagConstraints.CENTER);
	    addComponent( container, createObjPanel(), 0, 0, 1, 1, GridBagConstraints.NONE, GridBagConstraints.CENTER);
	    
	    // Some horizontal space between the left- and right-hand panels
	    addComponent( container, Box.createHorizontalStrut(30), 1, 0,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	    addComponent( container, Box.createHorizontalStrut(30), 1, 2,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	    
	    // Top-right panel defines the window parameters
	    addComponent( container, createWindowPanel(), 2, 0,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	    
	    // Horizontal separator across whole GUI to separate essential (above) from nice-to-have (below)
	    final JSeparator hsep = new JSeparator();
	    hsep.setBackground(SEPARATOR_BACK);
	    hsep.setForeground(SEPARATOR_FORE);
	    addComponent( container, hsep, 0, 1,  3, 1, GridBagConstraints.NONE, GridBagConstraints.CENTER);
	    final Dimension dim = container.getPreferredSize();
	    hsep.setPreferredSize(new Dimension(dim.width, SEPARATOR_WIDTH));
	    
	    // Middle-right panel defines the target info
	    addComponent( container, createTargetPanel(),   2, 2,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	    
	    
	    // Update the colours while ensuring that paste operations remian disabled in numeric fields
	    updateGUI();
	    
	    this.pack();
	    this.setVisible(true);
	    
	    this.addWindowListener(new WindowAdapter() {
	    	public void windowClosing(WindowEvent e){
	    		System.exit(0);
	    	}
	    }
	    );
	    
	    // Define timer to provide regular updating of timing information
	    // and to check whether windows are synchronised
	    // Task to perform
	    final ActionListener taskPerformer = new ActionListener() {
		    public void actionPerformed(ActionEvent event) {
		    	speed(TIMING_UPDATE);
		    	if(_areSynchronised()){
		    		syncWindows.setEnabled(false);
		    		syncWindows.setBackground(DEFAULT_COLOUR);
		    	}else{
		    		syncWindows.setEnabled(true);
		    		syncWindows.setBackground(WARNING_COLOUR);
		    	}
		    }
	    };

	    // Checks once per 2 seconds
	    final Timer tinfoTimer = new Timer(1000, taskPerformer);	
	    tinfoTimer.start();
	
	    
	    final ActionListener aladinOn = new ActionListener() {
		    public void actionPerformed(ActionEvent event) {
				
		    	if(aladin != null && aladinGo.isEnabled()){
		    		aladinGo.setEnabled(false);
		    	}
		    	if(aladin != null){
					// aladin has been initialise
					String status = aladin.execCommand("status");
					if (status.length() < 1){
						// but is not currently useable
						addTarg.setEnabled(false); // disable target buttons
						addComp.setEnabled(false);
						if(!aladinGo.isEnabled()) aladinGo.setEnabled(true); // allow re-enabling 
					}else{
						addTarg.setEnabled(true);
						addComp.setEnabled(true);
					}
		    	}
		    }
	    };
	    // Checks once per 10 seconds
	    final Timer aladinTimer = new Timer(10000, aladinOn);	
	    aladinTimer.start();
	    
	}catch (final Exception e) {	    
	    e.printStackTrace();
	    System.out.println(e);
	    System.out.println("Ufinder exiting.");
	    System.exit(0);
	}
    }
 
    /** Main program. Calls constructor and starts rtplot server */

    public static void main(final String[] args) {
    	mw = new ufinder();		
    }      
    
    	
    public void startAladin() {

    	// Instantiate Aladin
	System.out.println("Starting Aladin with target " + aladinTarget);
    	aladin = cds.aladin.Aladin.launch("-noreleasetest");    	
    	aladin.execCommand("sync");
    	String aladinSurvey=null;
    	if(surveyString.equals("DSS2-BLUE")) aladinSurvey = "Aladin(DSS2,J)";
    	if(surveyString.equals("DSS2-RED"))  aladinSurvey = "Aladin(DSS2,F)";
    	if(surveyString.equals("DSS1"))      aladinSurvey = "Aladin(DSS1)";
    	if(surveyString.equals("Aladin"))      aladinSurvey = "Aladin";
	System.out.println("get "+ aladinSurvey+" " + aladinTarget);
	String result = aladin.execCommand("get "+aladinSurvey+" " + aladinTarget);
	System.out.println("RESULT:  \n\n" + result + "\n\n"); 
    	aladin.execCommand("sync");

    	// now we should check if it has loaded and if not we should exit.
    	String aladinStatus = aladin.execCommand("status");
    	final String[] statusarray = aladinStatus.split("\\n");
    	String Image=null;
    	boolean ImageLoaded=false;
    	for(int i=0; i < statusarray.length-3; i++){
    		//System.out.println(statusarray[i]);
	    if(statusarray[i].contains("PlaneID") && statusarray[i+2].contains("Image") && 
	       !statusarray[i+3].contains("error")){
    			ImageLoaded = true;
    			Image = statusarray[i].substring(8, statusarray[i].length());
    		}
    	}
    	
    	if( ! ImageLoaded) {
    		aladin.execCommand("quit");
    		aladin = null;
		JOptionPane.showMessageDialog(mw,
					      "Aladin couldn't load the image you asked for. \nMaybe the survey selected doesn't cover this object?",
					      "Image load error",
					      JOptionPane.ERROR_MESSAGE);
    		return;
    	}
    	
    	// load appropriate size field of view for this telescope
    	if(_telescope.name.equalsIgnoreCase("wht")){
    		aladin.execCommand("zoom 1x");
    	} else if (_telescope.name.equalsIgnoreCase("ntt")){
    		aladin.execCommand("zoom 1x");
    	} else if (_telescope.name.equalsIgnoreCase("vlt")){
    		aladin.execCommand("zoom 2x");
    	}
    	if(_telescope.flipped) aladin.execCommand("flipflop H");
    	aladin.execCommand("sync");
    		
    	/** We get the RA and DEC of our pointing from the Aladin status command. This is necessary to
    	 * correctly load the ultracam field of view
    	 */	
    	aladinStatus = aladin.execCommand("status");
    	int start = aladinStatus.indexOf("Centre");
    	start+=8;
    	int stop = start+11;
    	final String RA = aladinStatus.substring(start, stop);
    	start=stop+1;
    	stop = start+11;
    	final String DEC = aladinStatus.substring(start, stop);
    	String[] raSplit = RA.split(":");
    	raHourVal.setValue(Integer.parseInt(raSplit[0]));
    	raMinVal.setValue(Integer.parseInt(raSplit[1]));
    	raSecVal.setValue(Double.parseDouble(raSplit[2]));
    	String[] decSplit = DEC.split(":");
    	if(decSplit[0].startsWith("+")){
    		decDegVal.setValue(Integer.parseInt(decSplit[0].substring(1,3)));
    	} else {
    		decDegVal.setText(decSplit[0]);
    	}
    	decMinVal.setValue(Integer.parseInt(decSplit[1]));
    	decSecVal.setValue(Double.parseDouble(decSplit[2]));
    	
    	// load FOV
    	FOVSync();
    	aladin.execCommand("sync");
    	
    	// run Sextractor to detect sources in Image and draw magnitude cirlces
    	// first run status to find the active image
	System.out.println(Image);
    	//aladin.execCommand("get Sextractor("+Image+",2.,24.,50000,1.2)");
    	aladin.execCommand("get Sextractor(Image)");
    	aladin.execCommand("sync");
    	// rename the catalog to have an easy name
    	aladin.execCommand("set S-ex* PlaneID=SexCat");
    	aladin.execCommand("sync");
    	aladin.execCommand("set SexCat Color=rgb(0,254,153)");
    	aladin.execCommand("sync");
    	aladin.execCommand("filter SMag {draw circle(-$[phot.mag*])}");
    	aladin.execCommand("sync");
    	aladin.execCommand("filter SMag on"); 
    	aladin.execCommand("sync");

    	// switch off annoying reticle
    	aladin.execCommand("reticle off");
    	aladin.execCommand("sync");
    	
    	//aladin.addObserver(mw,1);
    }
	
    public boolean setTarget(){
    	aladin.execCommand("createplane");
    	// we have to check that this was succesfull, otherwise delete the spurious catalog
    	String messg = aladin.execCommand("status");
    	if (messg.contains("NbObj   0")){
    		aladin.execCommand("rm New.cat");
    		return false;
    	}    	
    	aladin.execCommand("set New.cat Color=red");
    	aladin.execCommand("set New.cat PlaneID=Target");
    	aladin.execCommand("rm SMag");
    	aladin.execCommand("filter SMag {draw circle(-$[phot.mag*])}");
	aladin.execCommand("sync");
	aladin.execCommand("filter SMag on"); 
    	return true;
    }
    
    public boolean addComparison(){
    	aladin.execCommand("createplane");
    	// we have to check that this was succesfull, otherwise delete the spurious catalog
    	String messg = aladin.execCommand("status");
    	if (messg.contains("NbObj   0")){
    		aladin.execCommand("rm New.cat");
    		return false;
    	}    	
    	aladin.execCommand("set New.cat PlaneID=tmp");
    	aladin.execCommand("select tmp Comp");
    	aladin.execCommand("createplane");
    	aladin.execCommand("rm Comp");
    	aladin.execCommand("rm tmp");
    	aladin.execCommand("set New.cat Color=blue");
    	aladin.execCommand("set New.cat PlaneID=Comp");
    	aladin.execCommand("rm SMag");
    	aladin.execCommand("filter SMag {draw circle(-$[phot.mag*])}");
	aladin.execCommand("sync");
    	aladin.execCommand("filter SMag on"); 
    	return true;
    }

    public void publishChart(){
	
	String tempdir = System.getProperty("java.io.tmpdir");
	if ( !(tempdir.endsWith("/") || tempdir.endsWith("\\")) )
	    tempdir = tempdir + System.getProperty("file.separator");
	
	if(aladin != null) aladin.execCommand("hide SexCat");
	if(aladin != null) aladin.execCommand("save "+tempdir+"tmp.bmp");
	if(aladin != null) aladin.execCommand("show SexCat");
	
	File file = new File(tempdir+"tmp.bmp");
	// load image
	BufferedImage img = null;
	try {
	    img = ImageIO.read(file);
	    
	    // Create a graphics context on the buffered image
	    Graphics2D g2d = img.createGraphics();
	    int width = img.getWidth(this);
	    
	    // Draw on the image
	    // Set up Font
	    int ypos = 20;
	    g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
				 RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
	    g2d.setPaint(Color.black);
	    Font font = new Font("Arial", Font.PLAIN, 16);
		g2d.setFont(font);
		FontMetrics fontMetrics = g2d.getFontMetrics();
	    int h = fontMetrics.getHeight();
	
		//Create an alpha composite of 50%  
		AlphaComposite alpha = AlphaComposite.getInstance(AlphaComposite.SRC_ATOP);  
		g2d.setComposite(alpha);
	    
	    // Draw Object name, telescope pointing and window parameters
	    if(objText.getText().length() > 0) {
		String string = objText.getText();
		int w = fontMetrics.stringWidth(string);
		g2d.drawString(string,width-w-10,ypos);
	    }else{
		// print coordinates
		String string = coordText.getText();
		int w = fontMetrics.stringWidth(string);
		g2d.drawString(string,width-w-10,ypos);
	    }
	    // pointing parameters
	    String raText=null, decText=null, paText = null;
	    try {
		raText = raHourVal.getText() + ":" 
		    + raMinVal.getText() + ":" + String.valueOf(raSecVal.getValue());
		decText = decDegVal.getText() 
		    + ":" + decMinVal.getText() + ":" + decSecVal.getText();	    
		paText = String.valueOf(paDegVal.getValue());
	    } catch (Exception e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	    }		
	    if(raText.length() > 0){
		String string = "Tel RA: " + raText;
		int w = fontMetrics.stringWidth(string);
		g2d.drawString(string,width-w-10,ypos+=h);
		
		string = "Tel Dec: " + decText;
		w = fontMetrics.stringWidth(string);
		g2d.drawString(string,width-w-10,ypos+=h);
		
		string = "Tel PA: " + paText;
		w = fontMetrics.stringWidth(string);
		g2d.drawString(string,width-w-10,ypos+=h);
	    }
	    ypos+=h/2;
	    if(numEnable >0){
		String string = "ystart  xleft  xright  nx  ny";
		int w = fontMetrics.stringWidth(string);
		g2d.drawString(string,width-w-10,ypos+=h);
	    }
	    for(int i=0; i<numEnable; i++){
		try{
		    String string = ""+_windowPairs.getYstart(i)+ "   " +
			_windowPairs.getXleft(i)    + "  " + 
			_windowPairs.getXright(i)  + "  " +
			_windowPairs.getNx(i)        + "  " +
			_windowPairs.getNy(i);
		    int w = fontMetrics.stringWidth(string);
		    g2d.drawString(string,width-w-10,ypos+=h);
		} catch (Exception e) {
		    e.printStackTrace();
		}
	    }
	    g2d.dispose();
	    
	    // allow use to output image
	    final JFileChooser fc = new JFileChooser();
	    FileFilterBMP ff = new FileFilterBMP();
	    fc.setFileFilter(ff);
	    int returnVal = fc.showSaveDialog(this);
	    if (returnVal == JFileChooser.APPROVE_OPTION) {
		File ofile = fc.getSelectedFile();
		ImageIO.write(img,"bmp",ofile);
	    }
	} catch (IOException e) {
	}
	// clean up temp file
	file.delete();
    }
    
    // You own implementation of VOApp methods for Aladin callbacks
    public void position(double ra, double dec){}
    public String putVOTable(final VOApp app, final InputStream in,final String label) { return null; }
    public String putVOTable(final InputStream in,final String label) { return null; }
    public InputStream getVOTable(final String dataID) { return null; }
    public String putFITS(final InputStream in,final String label) { return null; }
    public InputStream getFITS(final String dataID) { return null; }
    public void showVOTableObject(final String oid[]) {
    	System.out.print("I have to show:");
    	for( int i=0; i<oid.length; i++ ) System.out.print(" "+oid[i]);
    	System.out.println();
    }
    public void selectVOTableObject(final String oid[]) {
    	System.out.print("I have to select:");
    	for( int i=0; i<oid.length; i++ ) System.out.print(" "+oid[i]);
    	System.out.println();
    }
    public String execCommand(final String cmd) {
    	
	displayArea.append(cmd + "\n");
		
    	// TO-DO: take cmd and parse it to get ra and dec numbers and roll value.
    	Pattern pattern= Pattern.compile("Target=.*");
    	Matcher matcher = pattern.matcher(cmd);
    	if(matcher.find()){
			// get ra and dec
			String DEC = matcher.group().substring(21, 35);
			String RA = matcher.group().substring(7, 20);
			String[] raSplit = RA.split(":");
			raHourVal.setValue(Integer.parseInt(raSplit[0]));
			raMinVal.setValue(Integer.parseInt(raSplit[1]));
			raSecVal.setValue(Double.parseDouble(raSplit[2]));
			String[] decSplit = DEC.split(":");
			displayArea.append(decSplit[0] + "\n");
			if(decSplit[0].startsWith("+")){
				decDegVal.setValue(Integer.parseInt(decSplit[0].substring(1,3)));
			} else {
				displayArea.append("negative dec " + decSplit[0] + "\n\n");
				decDegVal.setText(decSplit[0]);
			}
			decMinVal.setValue(Integer.parseInt(decSplit[1]));
			decSecVal.setValue(Double.parseDouble(decSplit[2]));
    	}
    	pattern=Pattern.compile("Roll=.*");
    	matcher = pattern.matcher(cmd);
    	if(matcher.find()){
			// get roll
    		final String toSearch = matcher.group();
    		pattern=Pattern.compile("\\d+");
    		matcher=pattern.matcher(toSearch);
    		if(matcher.find())
				paDegVal.setValue(Double.parseDouble(matcher.group()));
    	}
    	return null; 
	}
    public void addObserver(final VOObserver app,final int eventMasq) {}
     
    // Method for adding components to GridBagLayout for the window panel
    private static void addComponent (final Container cont, final Component comp, final int gridx, final int gridy, 
				      final int gridwidth, final int gridheight, final int fill, final int anchor){
	
	final GridBagConstraints gbc = new GridBagConstraints ();
	gbc.gridx      = gridx;
	gbc.gridy      = gridy;
	gbc.gridwidth  = gridwidth;
	gbc.gridheight = gridheight;
	gbc.fill       = fill;
	gbc.anchor     = anchor;
	gbLayout.setConstraints(comp, gbc);
	cont.add (comp);
    }
    
    /** Creates the panel which defines the object and telescope pointing parameters */
    public Component createObjPanel(){
    	int ypos=0; int xpos=0;
    	final JPanel _objPanel = new JPanel(gbLayout);
    	_objPanel.setBorder(new EmptyBorder(15,15,15,15));

	final JPanel telPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));

	// Radio Buttons to select telescope
	final JRadioButton[] telescopeButtons = new JRadioButton[TELESCOPE_DATA.length];
	final ButtonGroup telescopeGroup = new ButtonGroup();
	for(int ntel=0; ntel<TELESCOPE_DATA.length; ntel++){
	    telescopeButtons[ntel] = new JRadioButton(TELESCOPE_DATA[ntel].name);
	    
	    telescopeButtons[ntel].addActionListener(
						      new ActionListener(){
							  public void actionPerformed(final ActionEvent e){
							      TELESCOPE = ((JRadioButton)e.getSource()).getText();
							      // Set the current telescope 
							      for(int i=0; i<TELESCOPE_DATA.length; i++){
								  if(TELESCOPE_DATA[i].name.equals(TELESCOPE)){
								      _old_telescope = _telescope;
								      _telescope = TELESCOPE_DATA[i];
								      break;
								  }
							      }
							      if (aladin != null){
								  if(_old_telescope.flipped != _telescope.flipped)  
								      aladin.execCommand("flipflop H");
								  if(_telescope.name.equalsIgnoreCase("wht")){
								      aladin.execCommand("zoom 1x");
								  } else if (_telescope.name.equalsIgnoreCase("ntt")){
								      aladin.execCommand("zoom 1x");
								  } else if (_telescope.name.equalsIgnoreCase("vlt")){
								      aladin.execCommand("zoom 2x");
								  }}
							      FOVSync();
							  }});
	    telescopeGroup.add(telescopeButtons[ntel]);
	    telPanel.add(telescopeButtons[ntel]);
	}
	addComponent(_objPanel, telPanel, 1,ypos++,5,1,GridBagConstraints.NONE, GridBagConstraints.WEST);			 

    	// Add some space before we get onto the pointing definitions
    	addComponent( _objPanel, Box.createVerticalStrut(10), 0, ypos++,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);

	// Select the current telescope 
	for(int i=0; i<TELESCOPE_DATA.length; i++){
	    if(TELESCOPE_DATA[i].name.equals(TELESCOPE)){
		telescopeButtons[i].setSelected(true);
		break;
	    }
	}

	// entry box for object name
    	final JLabel objLabel = new JLabel("Object Name  ");
    	addComponent(_objPanel,objLabel,0,ypos,1,1,
    			GridBagConstraints.NONE, GridBagConstraints.WEST);
    	
    	objText.addActionListener(
    			new ActionListener(){
    				public void actionPerformed(final ActionEvent e){
    				if(objText.getText().length() > 0) {
    					aladinGo.setEnabled(true);
    				}else{
    					aladinGo.setEnabled(false);
    				}
    				}
    			}
    	);
    	addComponent(_objPanel,objText,1,ypos++,9,1,
    			GridBagConstraints.NONE, GridBagConstraints.WEST);
    	
    	final JLabel coordLabel = new JLabel("or Coords ");
    	addComponent(_objPanel,coordLabel,0,ypos,1,1,
    			GridBagConstraints.NONE, GridBagConstraints.WEST);
    	
    	coordText.addActionListener(
    			new ActionListener(){
    				public void actionPerformed(final ActionEvent e){
    				if(coordText.getText().length() > 0) {
    					aladinGo.setEnabled(true);
    				}else{
    					aladinGo.setEnabled(false);
    				}
    				}
    			}
    	);
    	addComponent(_objPanel,coordText,1,ypos++,9,1,
    			GridBagConstraints.NONE, GridBagConstraints.WEST);
    	
    	final JLabel surveyLabel = new JLabel("Survey");
    	addComponent(_objPanel,surveyLabel,0,ypos,1,1,
    			GridBagConstraints.NONE, GridBagConstraints.WEST);
    	
    	surveyChoice = new JComboBox(SURVEY_LABEL);
    	surveyChoice.setSelectedItem(surveyString);
    	surveyChoice.setMaximumRowCount(SURVEY_LABEL.length);
    	surveyChoice.addActionListener(
    			new ActionListener(){
    				public void actionPerformed(final ActionEvent e){
    					surveyString = (String) surveyChoice.getSelectedItem();
    					if(objText.getText().length() > 0 || 
    							coordText.getText().length() > 0) aladinGo.setEnabled(true);
    				}
    			}
    	);
    	
    	addComponent(_objPanel,surveyChoice,1,ypos++,9,1,
    			GridBagConstraints.NONE, GridBagConstraints.WEST);
   
    	
    	aladinGo.setEnabled(false);    		
    	aladinGo.addActionListener(
				   new ActionListener(){
				       public void actionPerformed(final ActionEvent e) {
					   if(coordText.getText().length() > 0){
					       aladinTarget = coordText.getText();
					       aladinGo.setEnabled(false);	

					       Thread t = new Thread(new aladinInstance()); 
					       t.start();
					   }else if(objText.getText().length() > 0){
					       aladinTarget = objText.getText();
					       aladinGo.setEnabled(false);	

					       Thread t = new Thread(new aladinInstance()); 
					       t.start();
					   }
				       }		
				   }
				   );		
    	addComponent( _objPanel, aladinGo, 1, ypos++, 9, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
    	
    	// Add some space before we get onto the pointing definitions
    	addComponent( _objPanel, Box.createVerticalStrut(10), 0, ypos++,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
      	
    	final JPanel ra = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    	final JPanel dec = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    	final JLabel raLabel = new JLabel("Tel. RA");
    	final JLabel decLabel = new JLabel("Tel. Dec");
    
    	raHourVal.addActionListener(this);
    	raMinVal.addActionListener(this);
    	raSecVal.addActionListener(this);
    	decDegVal.addActionListener(this);
    	decMinVal.addActionListener(this);
    	decSecVal.addActionListener(this);
      	paDegVal.addActionListener(this);
      	
    	ra.add(raHourVal);
    	ra.add(new JLabel(" : "));
    	ra.add(raMinVal);
    	ra.add(new JLabel(" : "));
    	ra.add(raSecVal);
    	dec.add(decDegVal);
    	dec.add(new JLabel(" : "));
    	dec.add(decMinVal);
    	dec.add(new JLabel(" : "));
    	dec.add(decSecVal);
    	addComponent(_objPanel,raLabel,0,ypos,1,1,
    			GridBagConstraints.NONE, GridBagConstraints.WEST);    	
    	addComponent(_objPanel,ra,1,ypos++,10,1,
    			GridBagConstraints.NONE, GridBagConstraints.WEST);
       	addComponent(_objPanel,decLabel,0,ypos,1,1,
    			GridBagConstraints.NONE, GridBagConstraints.WEST);    	
    	addComponent(_objPanel,dec,1,ypos++,10,1,
    			GridBagConstraints.NONE, GridBagConstraints.WEST);

     	final JLabel paLabel = new JLabel("Tel. PA");
    	addComponent(_objPanel,paLabel,0,ypos,1,1,
    			GridBagConstraints.NONE, GridBagConstraints.WEST);    	   
     	addComponent(_objPanel,paDegVal,1,ypos++,1,1,
    			GridBagConstraints.NONE, GridBagConstraints.WEST);
     	
//      Add some space before we get onto the buttons to add targets
    	addComponent( _objPanel, Box.createVerticalStrut(10), 0, ypos++,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
      	  	
    	addTarg.setEnabled(false);    		
    	addTarg.addActionListener(
			new ActionListener(){
				public void actionPerformed(final ActionEvent e) {
						if (setTarget()) addTarg.setEnabled(false);
				}		
			}
    	);		
    	addComponent( _objPanel, addTarg, 1, ypos++, 9, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
    	
    	addComp.setEnabled(false);    		
    	addComp.addActionListener(
			new ActionListener(){
				public void actionPerformed(final ActionEvent e) {
						addComparison();
				}		
			}
    	);		
    	addComponent( _objPanel, addComp, 1, ypos++, 9, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
    	

    	_objPanel.setBorder(new EmptyBorder(15,15,15,15));   	
    	return _objPanel;
    }
    
    /** Creates the panel which defines the window parameters */
    public Component createWindowPanel(){
	
	int ypos = 0;
	
	final JPanel _windowPanel     = new JPanel( gbLayout );
	_windowPanel.setBorder(new EmptyBorder(15,15,15,15));
	
	// Application (drift etc)
	final JLabel templateLabel = new JLabel("Template type");
	addComponent( _windowPanel, templateLabel, 0, ypos,  1, 1, 
		      GridBagConstraints.NONE, GridBagConstraints.WEST);
	
	templateChoice = new JComboBox(TEMPLATE_LABEL);
	templateChoice.setSelectedItem(applicationTemplate);
	templateChoice.setMaximumRowCount(TEMPLATE_LABEL.length);
	
	// The main thing to do here is disable irrelevant parts according to 
	// the application, and to change the FOV in aladin
	templateChoice.addActionListener(
			
			new ActionListener(){
				public void actionPerformed(final ActionEvent e){
					
					applicationTemplate = (String) templateChoice.getSelectedItem();
					setNumEnable();
					_windowPairs.setNpair(numEnable);
					if(numEnable == 0) {
						_setWinLabels(false);
					}else{
						_setWinLabels(true);
					}
					FOVSync();
					oldApplicationTemplate = applicationTemplate;
				}	     
			}
	);
					 			
	
	// Add to the panel
	addComponent( _windowPanel, templateChoice, 1, ypos++,  5, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	
	// Readout speed selection
	addComponent( _windowPanel,  new JLabel("Readout speed"), 0, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	
	speedChoice = new JComboBox(SPEED_LABELS);
	speedChoice.setSelectedItem(readSpeed);
	addComponent( _windowPanel, speedChoice, 1, ypos++,  5, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	
	// Exposure time
	addComponent( _windowPanel, new JLabel("Exposure delay (millisecs)   "), 0, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	
	// Might need to adjust fine increment after a change of exposure time
	exposeText.addActionListener(new ActionListener(){
		public void actionPerformed(final ActionEvent e){
		    if(!EXPERT_MODE){
			try {
			    final int n = exposeText.getValue();
			    if(n == 0)
				tinyExposeText.setValue(5);
			    else
				tinyExposeText.setValue(0);
			    expose = 5;
			} 
			catch (final Exception er) {
			    tinyExposeText.setValue(0);
			}
		    }
		}
	    });

	final JPanel exp = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
	exp.add(exposeText);
	addComponent( _windowPanel, exp, 1, ypos++,  5, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
		
	// The binning factors
	addComponent( _windowPanel, new JLabel("Binning factors (X, Y)"), 0, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	addComponent( _windowPanel, xbinText, 1, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	addComponent( _windowPanel, ybinText, 2, ypos++,  2, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	
	// Add some space before we get onto the window definitions
	addComponent( _windowPanel, Box.createVerticalStrut(10), 0, ypos++,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);

	// Ensure that binned windows match a standard phasing (designed so that there are no gaps
	// in the middle of the chip
	syncWindows.setEnabled(false);
	syncWindows.addActionListener(
			new ActionListener(){
				public void actionPerformed(final ActionEvent e) {
					if(_syncWindows()){
						syncWindows.setEnabled(false);						  
						syncWindows.setBackground(DEFAULT_COLOUR);
					}		
				}		
			}
	);		
	addComponent( _windowPanel, syncWindows, 1, ypos++, 2, 1, GridBagConstraints.NONE, GridBagConstraints.CENTER);

	// Add some space before we get onto the window definitions
	addComponent( _windowPanel, Box.createVerticalStrut(10), 0, ypos++,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	
	// Window definition lines
	setNumEnable();
	
	// First the labels for each column
	ystartLabel.setToolTipText("Y value of lowest row of window pair");
	xleftLabel.setToolTipText("X value of first column of left-hand window");
	xrightLabel.setToolTipText("X value of first column of right-hand window");
	nxLabel.setToolTipText("Number of unbinned pixels in X of each window of pair");
	nyLabel.setToolTipText("Number of unbinned pixels in Y of each window of pair");
	int xpos = 0;
	addComponent( _windowPanel, windowsLabel, xpos++, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	addComponent( _windowPanel, ystartLabel,  xpos++, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.CENTER);
	addComponent( _windowPanel, xleftLabel,   xpos++, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.CENTER);
	addComponent( _windowPanel, xrightLabel,  xpos++, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.CENTER);
	addComponent( _windowPanel, nxLabel,      xpos++, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.CENTER);
	addComponent( _windowPanel, nyLabel,      xpos++, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.CENTER);
	ypos++;
	
	// Then the row labels and fields for integer input
	_windowPairs = new WindowPairs(gbLayout, _windowPanel, ypos, xbin, ybin, DEFAULT_COLOUR, ERROR_COLOUR);
	_windowPairs.setNpair(numEnable);
	_windowPairs.addActionListener(new ActionListener(){
		public void actionPerformed(final ActionEvent e) {
			FOVSync();
		}
	});
	ypos += 3;
	
	// Add some space between window definitions and the user-defined stuff
	addComponent( _windowPanel, Box.createVerticalStrut(20), 0, ypos++,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);

	// Add a border
	_windowPanel.setBorder(new EmptyBorder(15,15,15,15));	
	return _windowPanel;
    }

    /** Creates the panel displaying the timing & signal-to-noise information */
    public Component createTimingPanel(){
	
	// Timing info panel
	final JPanel _timingPanel = new JPanel(gbLayout);
	
	int ypos = 0;
	
	addComponent( _timingPanel, new JLabel("Frame rate (Hz)"), 0, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	_frameRate.setEditable(false);
	addComponent( _timingPanel, _frameRate, 1, ypos++,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	
	final JLabel cycle = new JLabel("Cycle time (s)");
	cycle.setToolTipText("Time from start of one exposure to the start of the next");
	addComponent( _timingPanel, cycle, 0, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	_cycleTime.setEditable(false);
	addComponent( _timingPanel, _cycleTime, 1, ypos++,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	
	final JLabel duty = new JLabel("Duty cycle (%)");
	duty.setToolTipText("Percentage of time spent gathering photons");
	addComponent( _timingPanel, duty, 0, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	_dutyCycle.setEditable(false);
	addComponent( _timingPanel, _dutyCycle, 1, ypos++,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	
	addComponent( _timingPanel, Box.createVerticalStrut(10), 0, ypos++,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	
	final JLabel totalLabel = new JLabel("Total counts/exposure");
	totalLabel.setToolTipText("Total counts/exposure in object, for an infinite radius photometric aperture");
	addComponent( _timingPanel, totalLabel, 0, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	_totalCounts.setEditable(false);
	addComponent( _timingPanel, _totalCounts, 1, ypos++,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	
	final JLabel peakLabel = new JLabel("Peak counts/exposure  ");
	peakLabel.setToolTipText("In a binned pixel");
	addComponent( _timingPanel,  peakLabel, 0, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	_peakCounts.setEditable(false);
	addComponent( _timingPanel, _peakCounts, 1, ypos++,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	
	final JLabel stonLabelOne = new JLabel("S-to-N");
	stonLabelOne.setToolTipText("Signal-to-noise in one exposure, 1.5*seeing aperture");
	addComponent( _timingPanel,  stonLabelOne, 0, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	_signalToNoiseOne.setEditable(false);
	addComponent( _timingPanel, _signalToNoiseOne, 1, ypos++,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	
	final JLabel stonLabel = new JLabel("S-to-N, 3 hr");
	stonLabel.setToolTipText("Total signal-to-noise in a 3 hour run, 1.5*seeing aperture");
	addComponent( _timingPanel,  stonLabel, 0, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	_signalToNoise.setEditable(false);
	addComponent( _timingPanel, _signalToNoise, 1, ypos++,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	return _timingPanel;
    }
    
    //------------------------------------------------------------------------------------------------------------------------------------------
    /** Creates the panel defining the target information */
    public Component createTargetPanel(){
	
	int ypos = 0;
	
	// Target info panel
	final JPanel _targetPanel = new JPanel(gbLayout);
	
	final JLabel bandLabel = new JLabel("Bandpass");
	bandLabel.setToolTipText("Bandpass for estimating counts and signal-to-noise");
	addComponent( _targetPanel, bandLabel,     0, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	
	// Create radio buttons for the filters
	final JRadioButton uButton = new JRadioButton("u'     ");
	uButton.addActionListener(new ActionListener(){public void actionPerformed(final ActionEvent e){_filterIndex = 0;}});
	addComponent( _targetPanel, uButton,     1, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	
	final JRadioButton gButton = new JRadioButton("g'     ");
	gButton.setSelected(true);
	gButton.addActionListener(new ActionListener(){public void actionPerformed(final ActionEvent e){_filterIndex = 1;}});
	addComponent( _targetPanel, gButton,     2, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	
	final JRadioButton rButton = new JRadioButton("r'     ");
	rButton.addActionListener(new ActionListener(){public void actionPerformed(final ActionEvent e){_filterIndex = 2;}});
	addComponent( _targetPanel, rButton,     3, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	
	final JRadioButton iButton = new JRadioButton("i'     ");
	iButton.addActionListener(new ActionListener(){public void actionPerformed(final ActionEvent e){_filterIndex = 3;}});
	addComponent( _targetPanel, iButton,     4, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	
	final JRadioButton zButton = new JRadioButton("z'");
	zButton.addActionListener(new ActionListener(){public void actionPerformed(final ActionEvent e){_filterIndex = 4;}});
	addComponent( _targetPanel, zButton,     5, ypos++,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	
	// Group the radio buttons.
	final ButtonGroup fGroup = new ButtonGroup();
	fGroup.add(uButton);
	fGroup.add(gButton);
	fGroup.add(rButton);
	fGroup.add(iButton);
	fGroup.add(zButton);
	
	final JLabel magLabel = new JLabel("Magnitude");
	magLabel.setToolTipText("Magnitude at airmass=0 for estimating counts and signal-to-noise");
	addComponent( _targetPanel, magLabel,     0, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	addComponent( _targetPanel, _magnitudeText,     1, ypos++,  5, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	
	final JLabel seeingLabel = new JLabel("Seeing (FWHM, arcsec)     ");
	seeingLabel.setToolTipText("FWHM seeing. Aperture assumed to be 1.5 times this.");
	addComponent( _targetPanel, seeingLabel,     0, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	addComponent( _targetPanel, _seeingText,     1, ypos++,  5, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	
	final JLabel skyBackLabel = new JLabel("Sky brightness");
	addComponent( _targetPanel, skyBackLabel,     0, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	
	// Create radio buttons for the sky brightness
	final JRadioButton darkButton = new JRadioButton("dark");
	darkButton.addActionListener(new ActionListener(){public void actionPerformed(final ActionEvent e){_skyBrightIndex = 0;}});
	addComponent( _targetPanel, darkButton,     1, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	
	final JRadioButton greyButton = new JRadioButton("grey");
	greyButton.setSelected(true);
	greyButton.addActionListener(new ActionListener(){public void actionPerformed(final ActionEvent e){_skyBrightIndex = 1;}});
	addComponent( _targetPanel, greyButton,     2, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	
	final JRadioButton brightButton = new JRadioButton("bright");
	brightButton.addActionListener(new ActionListener(){public void actionPerformed(final ActionEvent e){_skyBrightIndex = 2;}});
	addComponent( _targetPanel, brightButton,     3, ypos++,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	
	// Group the radio buttons.
	final ButtonGroup sGroup = new ButtonGroup();
	sGroup.add(darkButton);
	sGroup.add(greyButton);
	sGroup.add(brightButton);
	
	final JLabel airmassLabel = new JLabel("Airmass");
	addComponent( _targetPanel, airmassLabel,     0, ypos,  1, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	addComponent( _targetPanel, _airmassText,     1, ypos++,  5, 1, GridBagConstraints.NONE, GridBagConstraints.WEST);
	
	_targetPanel.setBorder(new EmptyBorder(15,15,15,15));	
	return _targetPanel;
    }


    //------------------------------------------------------------------------------------------------------------------------------------------
    
    // Create the "File" menu
    private JMenu createFileMenu() {
	
	final JMenu fileMenu = new JMenu("File");
	
	// Add actions to the "File" menu
	// Quit the program
	final JMenuItem _quit = new JMenuItem("Quit");
	_quit.addActionListener(
				new ActionListener(){
				    public void actionPerformed(final ActionEvent e){
					System.exit(0);
				    }
				});
	final JMenuItem _publish = new JMenuItem("Publish...");
	_publish.addActionListener(new ActionListener(){
		public void actionPerformed(final ActionEvent e){
		    publishChart();
		}
	    });

	// Slide Control Button
	final JMenuItem _tweak = new JMenuItem("Tweak acq...");
	_tweak.addActionListener(
				 new ActionListener(){
				     public void actionPerformed(ActionEvent e) {
					 SwingUtilities.invokeLater(new Runnable() {
						 public void run() {
						     JFrame newframe = new JFrame("Tweak Acq");
						     newframe.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
						     newframe.add(new Tweaker(_telescope));
						     newframe.pack();
						     newframe.setVisible(true);
						 }
					     });
				     }
				 });


	fileMenu.add(_publish);
	fileMenu.add(_tweak);
	fileMenu.add(_quit);

	return fileMenu;
    }

    
    //------------------------------------------------------------------------------------------------------------------------------------------
    
    // Create the "Settings" menu
    private JMenu createSettingsMenu() throws Exception {
	
	final JMenu settingsMenu = new JMenu("Settings");	
	
	// Telescope choices
	
	return settingsMenu;
    }

  //------------------------------------------------------------------------------------------------------------------------------------------



    /** This routine implements's Vik's speed computations and reports
     *	the frame rate in Hertz, the cycle time (e.g. sampling time),
     * exposure time (time on source per cycle), the dead time and readout
     * time, all in seconds. Finally it also reports the duty cycle, and
     * in the case of drift mode, the number of windows in the storage area
     * along with the pipe shift in pixels.
     */

    public double speed(final int method) {

	try{

	    if(isValid(_validStatus)){
		
		// Set the readout speed
		readSpeed = (String) speedChoice.getSelectedItem();
		double cdsTime, clearTime, frameTransfer, readout, video;
		double cycleTime, frameRate, exposureTime, deadTime;
		int nwins = 0, pshift = 0;
		
		if(readSpeed.equals("Fast")){
		    cdsTime = CDS_TIME_FBB;
		}else if(readSpeed.equals("Turbo")){
		    cdsTime = CDS_TIME_FDD;
		}else if(readSpeed.equals("Slow")){
		    cdsTime = CDS_TIME_CDD;
		}else{
		    throw new Error("readSpeed = \"" + readSpeed + "\" is unrecognised. Programming error");
		}
		video = cdsTime + SWITCH_TIME;
		
		if(applicationTemplate.equals("Fullframe + clear") || applicationTemplate.equals("Fullframe, no clear")){
		    
		    frameTransfer = 1033*VCLOCK_FRAME;
		    readout       = (VCLOCK_STORAGE*ybin + 536.*HCLOCK + (512./xbin+2)*video)*(1024./ybin);		
		    if(applicationTemplate.equals("Fullframe + clear")){
			clearTime    = (1033 + 1027)*VCLOCK_FRAME;
			cycleTime    = (INVERSION_DELAY + 100*expose + clearTime + frameTransfer + readout)/1.e6;
			exposureTime = expose/10000.;
		    }else{
			cycleTime    = (INVERSION_DELAY + 100*expose + frameTransfer + readout)/1.e6;
			exposureTime = cycleTime - frameTransfer/1.e6;
		    }
		    readout      /= 1.e6;
		    
		}else if(applicationTemplate.equals("Fullframe with overscan")){
		    
		    clearTime     = (1033. + 1032.) * VCLOCK_FRAME;
		    frameTransfer = 1033.*VCLOCK_FRAME;
		    readout       = (VCLOCK_STORAGE*ybin + 540.*HCLOCK + ((540./xbin)+2.)*video)*(1032/ybin);
		    cycleTime     = (INVERSION_DELAY + 100*expose + clearTime + frameTransfer + readout)/1.e6;
		    exposureTime  = expose/10000.;
		    readout      /= 1.e6;
		    
		}else if(applicationTemplate.equals("2 windows") || applicationTemplate.equals("4 windows") || 
			 applicationTemplate.equals("6 windows") || applicationTemplate.equals("2 windows + clear") ){
		    
		    if(applicationTemplate.equals("2 windows + clear") ){
			clearTime     = (1033 + 1027)*VCLOCK_FRAME;
		    }else{
			clearTime = 0.;
		    }
		    frameTransfer = 1033.*VCLOCK_FRAME;
		    cycleTime     = INVERSION_DELAY + 100*expose + frameTransfer + clearTime;
		    readout       = 0.;
		    
		    for(int i=0; i<numEnable; i++){
			
			final int ystart = _windowPairs.getYstart(i);
			final int xleft  = _windowPairs.getXleft(i);
			final int xright = _windowPairs.getXright(i);
			final int nx     = _windowPairs.getNx(i);
			final int ny     = _windowPairs.getNy(i);
			
			final int ystart_m = i > 0 ? _windowPairs.getYstart(i-1) : 1;
			final int ny_m     = i > 0 ? _windowPairs.getNy(i-1)     : 0;
			
			// Time taken to shift the window next to the storage area
			final double yShift = i > 0 ? (ystart-ystart_m-ny_m)*VCLOCK_STORAGE : (ystart-1)*VCLOCK_STORAGE;
			
			// Number of columns to shift whichever window is further from the edge of the readout
			// to get ready for simultaneous readout.
			final int diffShift = Math.abs(xleft - 1 - (1024 - xright - nx + 1) );
			
			// Time taken to dump any pixels in a row that come after the ones we want.
			// The '8' is the number of HCLOCKs needed to open the serial register dump gates
			// If the left window is further from the left edge than the right window is from the
			// right edge, then the diffshift will move it to be the same as the right window, and
			// so we use the right window parameters to determine the number of hclocks needed, and
			// vice versa.
			final int numHclocks   = (xleft - 1 > 1024-xright-nx+1) ?
			    nx + diffShift + (1024 - xright - nx + 1) + 8 :
			    nx + diffShift + (xleft - 1) + 8;
			
			// Time taken to read one line. The extra 2 is required to fill the video pipeline buffer
			final double lineRead = VCLOCK_STORAGE*ybin + numHclocks*HCLOCK + (nx/xbin+2)*video;
			
			// Time taken to read window
			final double read     = (ny/ybin)*lineRead;
			
			cycleTime += yShift + read;
			readout   += yShift + read;
		    }
		    
		    // Convert to microseconds
		    if(applicationTemplate.equals("2 windows + clear") ){
			exposureTime = expose/10000.;
		    }else{
			exposureTime = (cycleTime - frameTransfer)/1.e6;
		    }

		    cycleTime   /= 1.e6;
		    readout     /= 1.e6;
		    
		}else if(applicationTemplate.equals("Drift mode")){		
		    
		    final int ystart = _windowPairs.getYstart(0);
		    final int xleft  = _windowPairs.getXleft(0);
		    final int xright = _windowPairs.getXright(0);
		    final int nx     = _windowPairs.getNx(0);
		    final int ny     = _windowPairs.getNy(0);
		    
		    // Drift mode
		    nwins  = (int)(((1033. / ny ) + 1.)/2.);
		    pshift = (int)(1033.-(((2.*nwins)-1.)*ny));
		    
		    frameTransfer = (ny + ystart - 1.)*VCLOCK_FRAME;
		    final int diffShift   = Math.abs(xleft - 1 - (1024-xright-nx+1));
		    final int numHclocks  = (xleft - 1 > 1024-xright-nx+1) ?
			nx + diffShift + (1024-xright-nx+1) + 8 :
			nx + diffShift + (xleft-1) + 8;
		    final double lineRead = VCLOCK_STORAGE*ybin + numHclocks*HCLOCK + (nx/xbin+2)*video;
		    final double read     = (ny/ybin)*lineRead;
		    
		    cycleTime    = (INVERSION_DELAY + pshift*VCLOCK_STORAGE + 100*expose + frameTransfer + read)/1.e6;
		    exposureTime = cycleTime - frameTransfer/1.e6;
		    readout      = (read + pshift*VCLOCK_STORAGE)/1.e6;

		}else if(applicationTemplate.equals("Timing test")){		
		    
		    // Same as drift mode except no compensating delays are added, so on average
		    // there is only one pipe shift per nwin frames
		    final int ystart = _windowPairs.getYstart(0);
		    final int xleft  = _windowPairs.getXleft(0);
		    final int xright = _windowPairs.getXright(0);
		    final int nx     = _windowPairs.getNx(0);
		    final int ny     = _windowPairs.getNy(0);
		    
		    // Drift mode
		    nwins  = (int)(((1033. / ny ) + 1.)/2.);
		    pshift = (int)(1033.-(((2.*nwins)-1.)*ny));
		    
		    frameTransfer = (ny + ystart - 1.)*VCLOCK_FRAME;
		    final int diffShift   = Math.abs(xleft - 1 - (1024-xright-nx+1));
		    final int numHclocks  = (xleft - 1 > 1024-xright-nx+1) ?
			nx + diffShift + (1024-xright-nx+1) + 8 :
			nx + diffShift + (xleft-1) + 8;
		    final double lineRead = VCLOCK_STORAGE*ybin + numHclocks*HCLOCK + (nx/xbin+2)*video;
		    final double read     = (ny/ybin)*lineRead;
		    
		    cycleTime    = (INVERSION_DELAY + (pshift*VCLOCK_STORAGE)/nwins + 100*expose + frameTransfer + read)/1.e6;
		    exposureTime = cycleTime - frameTransfer/1.e6;
		    readout      = (read + (pshift*VCLOCK_STORAGE)/nwins)/1.e6;
		    
		}else{
		    throw new Error("Application = \"" + applicationTemplate + "\" is unrecognised. Programming error in speed");
		}
		deadTime  = cycleTime - exposureTime;
		frameRate = 1./cycleTime;

		if(method == CYCLE_TIME_ONLY)
		    return cycleTime;

		// Signal-to-noise info. Not a disaster if we fail to compute this, so
		// make sure that we can recover from failures with a try block
		final double AP_SCALE = 1.5;
		double zero = 0., sky = 0., skyTot = 0., gain = 0., read = 0., darkTot = 0.;
		double total = 0., peak = 0., correct = 0., signal = 0., readTot = 0., seeing = 0.;
		double noise = 1., skyPerPixel = 0., narcsec = 0., npix = 0., signalToNoise = 0.;
		try {

		    // Get the parameters for magnitudes
		    zero    = _telescope.zeroPoint[_filterIndex];
		    final double mag     = _magnitudeText.getValue();
		    seeing  = _seeingText.getValue();
		    sky     = SKY_BRIGHT[_skyBrightIndex][_filterIndex];
		    final double airmass = _airmassText.getValue();
		    if(readSpeed.equals("Fast")){
				gain = GAIN_FAST;
		    }else if(readSpeed.equals("Turbo")){
				gain = GAIN_TURBO;
		    }else if(readSpeed.equals("Slow")){
				gain = GAIN_SLOW;
		    }
		    int binIndex;
		    switch(Math.max(xbin,ybin)){
		    case 1:
			binIndex = 0;
			break;
		    case 2:
		    case 3:
			binIndex = 1;
			break;
		    case 4:
		    case 5:
		    case 6:
			binIndex = 2;
			break;
		    default:
			binIndex = 3;
		    }
		    if(readSpeed.equals("Fast")){
				read = READ_NOISE_FAST[binIndex];
		    }else if(readSpeed.equals("Turbo")){
				read = READ_NOISE_TURBO[binIndex];
		    }else if(readSpeed.equals("Slow")){
				read = READ_NOISE_SLOW[binIndex];
		    }

		    
		    final double plateScale = _telescope.plateScale;

		    // Now calculate expected counts
		    total = Math.pow(10.,(zero-mag-airmass*EXTINCTION[_filterIndex])/2.5)*exposureTime;
		    peak  = total*xbin*ybin*Math.pow(plateScale/(seeing/2.3548),2)/(2.*Math.PI);


		    // Work out fraction of flux in aperture with radius AP_SCALE*seeing
		    correct      = 1. - Math.exp(-Math.pow(2.3548*AP_SCALE, 2)/2.);

		    final double skyPerArcsec = Math.pow(10.,(zero-sky)/2.5)*exposureTime;
		    skyPerPixel = skyPerArcsec*Math.pow(plateScale,2)*xbin*ybin;
		    narcsec     = Math.PI*Math.pow(AP_SCALE*seeing,2);
		    skyTot      = skyPerArcsec*narcsec;
		    npix        = Math.PI*Math.pow(AP_SCALE*seeing/plateScale,2)/xbin/ybin;
		    signal      = correct*total;
		    darkTot     = npix*DARK_COUNT*exposureTime;
		    readTot     = npix*Math.pow(read, 2)/gain;
		    noise       = Math.sqrt( (readTot + darkTot + skyTot + signal) / gain);

		    // Now compute signal-to-noise in 3 hour seconds run
		    signalToNoise = signal/noise*Math.sqrt(3*3600./cycleTime);

		    _totalCounts.setText(round(total,1));

		    peak = (int)(100.*peak+0.5)/100.;
		    _peakCounts.setText(round(peak,2));
		    if(peak > 60000){
			_peakCounts.setBackground(ERROR_COLOUR);
		    }else if(peak > 25000){
			_peakCounts.setBackground(WARNING_COLOUR);
		    }else{
			_peakCounts.setBackground(DEFAULT_COLOUR);
		    }

		    _signalToNoise.setText(round(signalToNoise,1));
		    _signalToNoiseOne.setText(round(signal/noise,2));

		    _magInfo = true;

		}
		catch(final Exception e){
		    _totalCounts.setText("");
		    _peakCounts.setText("");
		    if(_magInfo)
			System.out.println(e.toString());
		    _magInfo = false;
		}

		final double dutyCycle = 100.*exposureTime/cycleTime;
		frameTransfer   /= 1.e6;

		// Update standard timing data fields
		_frameRate.setText(round(frameRate,3));
		_cycleTime.setText(round(cycleTime,4));
		_dutyCycle.setText(round(dutyCycle,2));

		if(method == DETAILED_TIMING){

		    final String pipeShift = (applicationTemplate.equals("Drift mode") || applicationTemplate.equals("Timing test")) ?
			String.valueOf(pshift) : new String("UNDEFINED");

		    final String nWindows = (applicationTemplate.equals("Drift mode") || applicationTemplate.equals("Timing test")) ?
			String.valueOf(nwins) : new String("UNDEFINED");

		    final Object[][] data = {
			{"Frame rate",       "=", round(frameRate,3),     "Hz"},
			{"Cycle time",       "=", round(cycleTime,4),     "sec"},
			{"Exposure time",    "=", round(exposureTime,4),  "sec"},
			{"Dead time",        "=", round(deadTime,4),      "sec"},
			{"Readout time",     "=", round(readout,4),       "sec"},
			{"Frame transfer",   "=", round(frameTransfer,4), "sec"},
			{"Duty cycle",       "=", round(dutyCycle,2),     "%"},
			{"Pipe shift",       "=", pipeShift,              "pixels"},
			{"nwin",             "=", nWindows,               "windows"},
			{"Zeropoint",        "=", round(zero,2),          "mags"},
			{"Read noise",       "=", round(read,2),          "counts RMS"},
			{"Gain",             "=", round(gain,2),           "electrons/count"},
			{"Aperture diameter","=", round(2.*AP_SCALE*seeing,1), "arcseconds"},
			{"Aperture area",    "=", round(npix,1),          "binned pixels"},
			{"Signal",           "=", round(total,1),         "total counts"},
			{"Signal",           "=", round(signal,1),        "counts in aperture"},
			{"Sky background",   "=", round(sky,2),           "mags/arcsec**2"},
			{"Sky background",   "=", round(skyPerPixel,2),   "counts/binned pixel"},
			{"Sky background",   "=", round(skyTot,1),        "counts in aperture"},
			{"Dark",             "=", round(darkTot,1),       "counts in aperture"},
			{"Read noise",       "=", round(readTot,0),       "effective counts in aperture"},
			{"Signal-to-noise",  "=", round(signal/noise,2),  "in single exposure"},
			{"Signal-to-noise",  "=", round(signalToNoise,1), "in 3 hour run"},
		    };
		    
		    final JTable table = new JTable(new TableModel(data));
		    table.setGridColor(DEFAULT_COLOUR);
		    table.getColumnModel().getColumn(0).setPreferredWidth(120);
		    table.getColumnModel().getColumn(1).setPreferredWidth(10);
		    table.getColumnModel().getColumn(2).setPreferredWidth(75);
		    table.getColumnModel().getColumn(3).setPreferredWidth(180);
		    
		    JOptionPane.showMessageDialog(this, table, "Timing details", JOptionPane.INFORMATION_MESSAGE);

		    return cycleTime;
		}
	    }else{
		_frameRate.setText("UNDEFINED");
		_cycleTime.setText("UNDEFINED");
		_dutyCycle.setText("UNDEFINED");
	    }
	}
	catch(final Exception e){
	    _frameRate.setText("UNDEFINED");
	    _cycleTime.setText("UNDEFINED");
	    _dutyCycle.setText("UNDEFINED");
	}
	return 0.;
    }

    //------------------------------------------------------------------------------------------------------------------------------------------

    // GUI update. It seems that updateComponentTree method re-enables the pastes on numeric fields where 
    // it was disabled. Thus we need to re-disable them all.

    public void updateGUI(){

	// Update colours
	SwingUtilities.updateComponentTreeUI(this);

	xbinText.setTransferHandler(null);
	ybinText.setTransferHandler(null);
	exposeText.setTransferHandler(null);
	tinyExposeText.setTransferHandler(null);
	numExposeText.setTransferHandler(null);
	_magnitudeText.setTransferHandler(null);
	_seeingText.setTransferHandler(null);
	_airmassText.setTransferHandler(null);
	_windowPairs.disablePaste();
    }

    //------------------------------------------------------------------------------------------------------------------------------------------
    // Modifies window locations so that a full frame NxM binned window can
    // be used as a bias. Does so by ensuring no gap in the middle of the CCDs
    private boolean _syncWindows() {
	if(isValid(true)){
	    try {
		if(applicationTemplate.equals("Fullframe + clear") || applicationTemplate.equals("Fullframe, no clear")){
		    
		    if(512 % xbin != 0 || 1024 % ybin != 0) 
			throw new Exception("Cannot synchronise fullframe with current binning factors. " +
					    "xbin must divide into 512, ybin must divide into 1024");
		    
		}else if(applicationTemplate.equals("Fullframe with overscan")){
		    
		    if(540 % xbin != 0 || 1032 % ybin != 0) 
			throw new Exception("Cannot synchronise fullframe+overscan with current binning factors. " +
					    "xbin must divide into 540, ybin must divide into 1032");
		}else{ 
		    
		    for(int i=0; i<numEnable; i++){
			_windowPairs.setYstartText(i, Integer.toString(_syncStart(_windowPairs.getYstart(i), ybin, 1,   1024)) );
			_windowPairs.setXleftText(i, Integer.toString(_syncStart(_windowPairs.getXleft(i),   xbin, 1,   512)) );
			_windowPairs.setXrightText(i, Integer.toString(_syncStart(_windowPairs.getXright(i), xbin, 513, 1024)) );
		    }

		}
		return true;
	    }
	    catch(final Exception e){
		return false;
	    }
	}
	return true;
    }

    // Synchronises window so that the binned pixels end at 512 and start at 513
    private int _syncStart(int start, final int bin, final int min, final int max){
	final int n = Math.round((float)((513-start))/bin);
	start = 513 - bin*n;
	if(start < min) start += bin;
	if(start > max) start -= bin;
	return start;
    }

    // Checks whether windows are synchronised
    private boolean _areSynchronised(){
	if(isValid(false)){
	    try{ 
		if(applicationTemplate.equals("Fullframe + clear") || applicationTemplate.equals("Fullframe, no clear")){
		    
		    if(512 % xbin != 0 || 1024 % ybin != 0) return false;
		    
		}else if(applicationTemplate.equals("Fullframe with overscan")){
		    
		    if(540 % xbin != 0 || 1032 % ybin != 0) return false;

		}else{

		    for(int i=0; i<numEnable; i++){
			if((513 - _windowPairs.getYstart(i)) % ybin != 0) return false;
			if((513 - _windowPairs.getXleft(i))  % xbin != 0) return false;
			if((513 - _windowPairs.getXright(i)) % xbin != 0) return false;
		    }

		}
		return true;
	    }
	    catch(final Exception e){
		return false;
	    }
	}
	return true;
    }

    //------------------------------------------------------------------------------------------------------------------------------------------

    /** This class is for the display of the detailed timing information in 'speed' */
    class TableModel extends AbstractTableModel {
	
	private Object[][] data;

	public TableModel(final Object[][] data){
	    this.data = data;
	}
			
	public int getColumnCount() {
	    return data[0].length;
	}
		    
	public int getRowCount() {
	    return data.length;
	}

	public Object getValueAt(final int row, final int col) {
	    return data[row][col];
	}

    }

    // Converts a double to a string rounding to specified number of decimals
    public String round(final double f, final int ndp){
	final DecimalFormat form = new DecimalFormat();
	form.setMaximumFractionDigits(ndp);
	return form.format(f);
    }

    /** Returns the index of the current application. Should be done with a map
     * but this will have to do for now.
     */
    private int _whichTemplate(){
	int iapp = 0;
	for(iapp=0; iapp<TEMPLATE_LABEL.length; iapp++)
	    if(applicationTemplate.equals(TEMPLATE_LABEL[iapp])) break;
	if(iapp == TEMPLATE_LABEL.length){
	    System.out.println("Template = " + applicationTemplate + " not recognised.");
	    System.out.println("This is a programming or configuration file error and the program will terminate.");
	    System.exit(0);
	}
	return iapp;
    }

    //------------------------------------------------------------------------------------------------------------------------------------------
		
    /** Sets the number of window pairs in use */
    public void setNumEnable(){
	try{
	    numEnable = Integer.parseInt(TEMPLATE_PAIR[_whichTemplate()]);
	}
	catch(final Exception e){
	    e.printStackTrace();
	    System.out.println(e);
	    System.out.println("Probable error in TEMPLATE_PAIR in configuration file = " + CONFIG_FILE);
	    System.out.println("This is a programming or configuration file error and the program will terminate.");
	    System.exit(0);
	}
    }

    //------------------------------------------------------------------------------------------------------------------------------------------

    // Enables the labels for the window pairs
    private void _setWinLabels(final boolean enable){
	ystartLabel.setEnabled(enable);
	xleftLabel.setEnabled(enable);
	xrightLabel.setEnabled(enable);
	nxLabel.setEnabled(enable);
	nyLabel.setEnabled(enable);
    }

    //------------------------------------------------------------------------------------------------------------------------------------------
	    
    /** Retrieves the values from the various fields and checks whether the currently 
     *  selected values represent a valid set of windows and sets. This should always
     *  be called by any routine that needs the most up-to-date values of the window parameters.
     */
    public boolean isValid(final boolean loud) {

	_validStatus = true;

	try{

	    xbin      = xbinText.getValue();	
	    ybin      = ybinText.getValue();	
	    expose    = _getExpose();
	    numExpose = numExposeText.getValue();

	    setNumEnable();
	    _validStatus = _windowPairs.isValid(xbin, ybin, numEnable, loud);
	    
	}
	catch(final Exception e){
	    if(loud)
	    _validStatus = false;
	}
	return _validStatus;
    }

    //------------------------------------------------------------------------------------------------------------------------------------------

    /** Get the exposure time from the two fields, one which gives the millsecond part, the other the 0.1 millsecond part
     * The application expect an integer number of 0.1 milliseconds
     */
    private int _getExpose() throws Exception {

	// Exposure specified in 0.1 milliseconds increments, but
	// this is too fine, so it is prompted for in terms of milliseconds.
	// This little program returns the value that must be sent to the servers

	expose  = 10*exposeText.getValue() + tinyExposeText.getValue();
	return expose;
    }

    //------------------------------------------------------------------------------------------------------------------------------------------
   //--------------------------------------------------------------------------------------------------------------
    // Load the configuration file

    public void loadConfig() throws Exception {

	final Properties properties = new Properties();
	if (CONFIG_FILE.equals("ufinder.conf")){
	    //let's try loading it from the class
            InputStream is = getClass().getClassLoader().getResourceAsStream("ufinder.conf");
	    properties.load(is);
	}else{
	    properties.load(new FileInputStream(CONFIG_FILE));
	}
	RTPLOT_SERVER_ON     = _loadBooleanProperty(properties, "RTPLOT_SERVER_ON");
	FILE_LOGGING_ON      = _loadBooleanProperty(properties, "FILE_LOGGING_ON");
	ULTRACAM_SERVERS_ON  = _loadBooleanProperty(properties, "ULTRACAM_SERVERS_ON");
	OBSERVING_MODE       = _loadBooleanProperty(properties, "OBSERVING_MODE");
	DEBUG                = _loadBooleanProperty(properties, "DEBUG");
	TELESCOPE            = _loadProperty(properties,        "TELESCOPE");

	// Set the current telescope 
	for(int i=0; i<TELESCOPE_DATA.length; i++){
	    if(TELESCOPE_DATA[i].name.equals(TELESCOPE)){
		_telescope = TELESCOPE_DATA[i];
		_old_telescope = _telescope;
		break;
	    }
	}

	if(_telescope == null){
	    String MESSAGE = "TELESCOPE = " + TELESCOPE + " was not found amongst the list of supported telescopes:\n";
	    for(int i=0; i<TELESCOPE_DATA.length-1; i++)
		MESSAGE += TELESCOPE_DATA[i].name + ", ";
	    MESSAGE += TELESCOPE_DATA[TELESCOPE_DATA.length-1].name;
	    throw new Exception(MESSAGE);
	}

	HTTP_CAMERA_SERVER   = _loadProperty(properties, "HTTP_CAMERA_SERVER");
	if(!HTTP_CAMERA_SERVER.trim().endsWith("/"))
	    HTTP_CAMERA_SERVER = HTTP_CAMERA_SERVER.trim() + "/";
	
	HTTP_DATA_SERVER    = _loadProperty(properties, "HTTP_DATA_SERVER");
	if(!HTTP_DATA_SERVER.trim().endsWith("/"))
	    HTTP_DATA_SERVER = HTTP_DATA_SERVER.trim() + "/";
	
	HTTP_PATH_GET         = _loadProperty(properties,        "HTTP_PATH_GET");
	HTTP_PATH_EXEC        = _loadProperty(properties,        "HTTP_PATH_EXEC");
	HTTP_PATH_CONFIG      = _loadProperty(properties,        "HTTP_PATH_CONFIG");
	HTTP_SEARCH_ATTR_NAME = _loadProperty(properties,        "HTTP_SEARCH_ATTR_NAME");
	APP_DIRECTORY         = _loadProperty(properties,        "APP_DIRECTORY");
	XML_TREE_VIEW         = _loadBooleanProperty(properties, "XML_TREE_VIEW");
	
	TEMPLATE_FROM_SERVER  = OBSERVING_MODE && _loadBooleanProperty(properties, "TEMPLATE_FROM_SERVER");
	final String dsep = System.getProperty("file.separator");
	
	TEMPLATE_DIRECTORY   = _loadProperty(properties, "TEMPLATE_DIRECTORY");
	if(!TEMPLATE_DIRECTORY.trim().endsWith(dsep))
	    TEMPLATE_DIRECTORY = TEMPLATE_DIRECTORY.trim() + dsep;
	
	EXPERT_MODE        = _loadBooleanProperty(properties, "EXPERT_MODE");
	LOG_FILE_DIRECTORY = _loadProperty(properties, "LOG_FILE_DIRECTORY");
	CONFIRM_ON_CHANGE  =  OBSERVING_MODE && _loadBooleanProperty(properties, "CONFIRM_ON_CHANGE");
	CHECK_FOR_MASK     =  OBSERVING_MODE && _loadBooleanProperty(properties, "CHECK_FOR_MASK");


	TEMPLATE_LABEL     = _loadSplitProperty(properties, "TEMPLATE_LABEL");
	
	TEMPLATE_PAIR      = _loadSplitProperty(properties, "TEMPLATE_PAIR");
	if(TEMPLATE_PAIR.length != TEMPLATE_LABEL.length)
	    throw new Exception("Number of TEMPLATE_PAIR = " + TEMPLATE_PAIR.length + 
				" does not equal the number of TEMPLATE_LABEL = " + TEMPLATE_LABEL.length);
	
	TEMPLATE_APP       = _loadSplitProperty(properties, "TEMPLATE_APP");
	if(TEMPLATE_APP.length != TEMPLATE_LABEL.length)
	    throw new Exception("Number of TEMPLATE_APP = " + TEMPLATE_APP.length + 
				" does not equal the number of TEMPLATE_LABEL = " + TEMPLATE_LABEL.length);
	
	TEMPLATE_ID        = _loadSplitProperty(properties, "TEMPLATE_ID");
	if(TEMPLATE_ID.length != TEMPLATE_LABEL.length)
	    throw new Exception("Number of TEMPLATE_ID = " + TEMPLATE_ID.length + 
				" does not equal the number of TEMPLATE_LABEL = " + TEMPLATE_LABEL.length);
	
	POWER_ON  = _loadProperty(properties, "POWER_ON");
	POWER_OFF = _loadProperty(properties, "POWER_OFF");
	

	
    }
        //------------------------------------------------------------------------------------------------------------------------------------------

    /** Splits up multiple arguments from configuration file */
    private String[] _loadSplitProperty(final Properties properties, final String key) throws Exception {
	final String propString = _loadProperty(properties, key);
	final StringTokenizer stringTokenizer = new StringTokenizer(propString, ";\n");
	final String[] multiString = new String[stringTokenizer.countTokens()];
	int i = 0;
	while(stringTokenizer.hasMoreTokens())
	    multiString[i++] = stringTokenizer.nextToken().trim();
	return multiString;
    }

    private String _loadProperty(final Properties properties, final String key) throws Exception {
	final String value = properties.getProperty(key);
	if(value == null)
	    throw new Exception("Could not find " + key + " in configration file " + CONFIG_FILE);
	return value;
    }

    //------------------------------------------------------------------------------------------------------------------------------------------

    /** Checks that a property has value YES or NO and returns true if yes. It throws an exception
     * if it neither yes nor no
     */
    private boolean _loadBooleanProperty(final Properties properties, final String key) throws Exception {
	final String value = properties.getProperty(key);
	if(value == null)
	    throw new Exception("Could not find " + key + " in configration file " + CONFIG_FILE);

	if(value.equalsIgnoreCase("YES") || value.equalsIgnoreCase("TRUE")){
	    return true;
	}else if(value.equalsIgnoreCase("NO") || value.equalsIgnoreCase("FALSE")){
	    return false;
	}else{
	    throw new Exception("Key " + key + " has value = " + value + " which does not match yes/no/true/false");
	}
    }

    //------------------------------------------------------------------------------------------------------------------------------------------

}
