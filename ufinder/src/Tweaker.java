package ufinder;

import javax.swing.*;
import javax.swing.text.*;
import org.jdesktop.layout.GroupLayout;

import java.awt.*;              //for layout managers and more
import java.awt.event.*;        //for action events
import java.awt.geom.AffineTransform; // for transformations
import java.text.DecimalFormat;


public class Tweaker extends JPanel 
    implements ActionListener {

    private String newline = "\n";


    // For rounding decimal numbers
    private static final DecimalFormat form = new DecimalFormat();
    int ndp = 1;

    // Colours and Fonts
    public static final Color DEFAULT_COLOUR    = new Color(220, 220, 255);
    public static final Color SEPARATOR_BACK    = new Color(100, 100, 100);
    public static final Color SEPARATOR_FORE    = new Color(150, 150, 200);
    public static final Color LOG_COLOUR        = new Color(240, 230, 255);
    public static final Color ERROR_COLOUR      = new Color(255, 0,   0  );
    public static final Color WARNING_COLOUR    = new Color(255, 100, 0  );
    public static final Color GO_COLOUR         = new Color(0,   255, 0  );
    public static final Color STOP_COLOUR       = new Color(255, 0,   0  );
    public static final Font DEFAULT_FONT = new Font("Dialog", Font.BOLD, 12);

    private static  Telescope tel;

    IntegerTextField rotField = new IntegerTextField(0,0,360,1,"Rot Position",true,DEFAULT_COLOUR, ERROR_COLOUR, 3, 1);
    IntegerTextField dxField = new IntegerTextField(0,-1024,1024,1,"X shift", true, DEFAULT_COLOUR, ERROR_COLOUR, 4, 1);
    IntegerTextField dyField = new IntegerTextField(0,-1024,1024,1,"Y shift", true, DEFAULT_COLOUR, ERROR_COLOUR, 4, 1);
    DoubleTextField oxField = new DoubleTextField(0.0,0.0,5.0,0.1,"RA offset", true,  DEFAULT_COLOUR, ERROR_COLOUR, 5);
    DoubleTextField oyField = new DoubleTextField(0.0,0.0,5.0,0.1,"Dec offset", true,  DEFAULT_COLOUR, ERROR_COLOUR, 5);
    JTextField[] fields = {rotField, dxField, dyField};

    /** This routine converts a shift in pixels and converts it to
     * offsets in RA and Dec, given a known telescope rotator
     * position.
     */
    public int calc_shift () {

	double rotdeg = Double.parseDouble(rotField.getText().replaceAll(",",""));
	double dx     = Double.parseDouble(dxField.getText().replaceAll(",",""));
	double dy     = Double.parseDouble(dyField.getText().replaceAll(",",""));

	double theta = Math.toRadians(rotdeg - tel.delta_pa);

	//creating the AffineTransform instance
	AffineTransform affineTransform = new AffineTransform();
	// set affineTransform to rotation about center
	affineTransform.setToRotation(-theta);

	// convert pixel shifts to units of arcsecs
	double dxa = dx * tel.plateScale;
	double dya = dy * tel.plateScale;
	double[] pixShiftInArcsec = {dxa, dya};
	double[] offsetsInArcsec = new double[2];
	affineTransform.transform(pixShiftInArcsec, 0, offsetsInArcsec, 0, 1);

	form.setMaximumFractionDigits(ndp);	
	oxField.setText(form.format(-offsetsInArcsec[0]));
	oyField.setText(form.format(-offsetsInArcsec[1]));
	return 1;
    }

    public Tweaker(Telescope _tel) {
	
	tel = _tel;

	// set look and feel
	GroupLayout layout = new GroupLayout(this);
	layout.setAutocreateGaps(true);
	layout.setAutocreateContainerGaps(true);
        setLayout(layout);
	setLookFeel();
	this.updateUI();

	// add action listener
	for  (int i=0; i < fields.length; i++){
	    fields[i].addActionListener(this);
	}

	JButton closeButton = new JButton("Close");
	closeButton.setActionCommand("close");
	closeButton.addActionListener(this);

	JPanel rotPanel = createRotPanel();
	JPanel pixPanel = createPixPanel();
	JPanel offPanel = createOffPanel();
	// pack into panel	
	layout.setHorizontalGroup(
				  layout.createParallelGroup(GroupLayout.CENTER)
				  .add(rotPanel)
				  .add(pixPanel)
				  .add(offPanel)
				  .add(closeButton)
				  );
						
	layout.setVerticalGroup(
				layout.createSequentialGroup()
				.add(rotPanel)
				.add(pixPanel)
				.add(offPanel)
				.add(closeButton)
				);

    }

    public void actionPerformed(ActionEvent e){
	if(e.getActionCommand().equals("close")){
	    this.getTopLevelAncestor().setVisible(false);
	}else if(e.getActionCommand().equals("calc")){
	    calc_shift();
	}
    }
    
    private JPanel createRotPanel() {

	// create a panel to hold rotator angle box
	JPanel rotPanel = new JPanel();
	GroupLayout layout = new GroupLayout(rotPanel);
	rotPanel.setLayout(layout);
	layout.setAutocreateGaps(true);
	layout.setAutocreateContainerGaps(true);
	//layout.linkSize(SwingConstants.VERTICAL, amountField, homeButton);
	rotPanel.setBorder(
			     BorderFactory.createCompoundBorder(
                                                                BorderFactory.createTitledBorder("Rotator on sky"),
                                                                BorderFactory.createEmptyBorder(2,2,2,2)
                                                                )
                             );

	layout.setHorizontalGroup(layout.createSequentialGroup().add(rotField));
	layout.setVerticalGroup(layout.createSequentialGroup().add(rotField));
				  

	// update to reflect current look and feel
	rotField.updateUI();

	// add action commands
	rotField.setActionCommand("calc");	

	return rotPanel;
    }

    private JPanel createPixPanel() {

	// create a panel to hold rotator angle box
	JPanel pixPanel = new JPanel();
	GroupLayout layout = new GroupLayout(pixPanel);
	pixPanel.setLayout(layout);
	layout.setAutocreateGaps(true);
	layout.setAutocreateContainerGaps(true);
	//layout.linkSize(SwingConstants.VERTICAL, amountField, homeButton);

	pixPanel.setBorder(
			     BorderFactory.createCompoundBorder(
                                                                BorderFactory.createTitledBorder("Pixel Shifts"),
                                                                BorderFactory.createEmptyBorder(2,2,2,2)
                                                                )
                             );

	JLabel xlabel = new JLabel("X: ");
	JLabel ylabel = new JLabel("Y: ");

	layout.setHorizontalGroup(layout.createSequentialGroup()
				  .add(xlabel)
				  .add(dxField)
				  .add(ylabel)
				  .add(dyField)
				  );
	layout.setVerticalGroup(layout.createParallelGroup(GroupLayout.CENTER)
				  .add(xlabel)
				  .add(dxField)
				  .add(ylabel)
				  .add(dyField)
				);
				  

	// update to reflect current look and feel
	dxField.updateUI();
	dyField.updateUI();

	// add action commands
	dxField.setActionCommand("calc");	
	dyField.setActionCommand("calc");	

	return pixPanel;
    }

    private JPanel createOffPanel() {

	// create a panel to hold rotator angle box
	JPanel offPanel = new JPanel();
	GroupLayout layout = new GroupLayout(offPanel);
	offPanel.setLayout(layout);
	layout.setAutocreateGaps(true);
	layout.setAutocreateContainerGaps(true);
	//layout.linkSize(SwingConstants.VERTICAL, amountField, homeButton);

	offPanel.setBorder(
			     BorderFactory.createCompoundBorder(
                                                                BorderFactory.createTitledBorder("Offsets (arcsecs)"),
                                                                BorderFactory.createEmptyBorder(2,2,2,2)
                                                                )
                             );

	JLabel xlabel = new JLabel("RA: ");
	JLabel ylabel = new JLabel("Dec: ");

	layout.setHorizontalGroup(layout.createSequentialGroup()
				  .add(xlabel)
				  .add(oxField)
				  .add(ylabel)
				  .add(oyField)
				  );
	layout.setVerticalGroup(layout.createParallelGroup(GroupLayout.CENTER)
				  .add(xlabel)
				  .add(oxField)
				  .add(ylabel)
				  .add(oyField)
				);
				  

	// update to reflect current look and feel
	oxField.updateUI();
	oyField.updateUI();

	return offPanel;
    }

    private void setLookFeel(){

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

    }
 
}
