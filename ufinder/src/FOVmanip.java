package ufinder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import java.net.*;

public class FOVmanip {
    
    private static DocumentBuilder _documentBuilder;
    private static Transformer     _transformer;
    private Document  document=null;
    private URL FileURL =  getClass().getClassLoader().getResource("UCAMFOV.xml");
    private static NodeList nodelist=null;
    private Node daddy=null;
    
    /** Constructor
     * 
     */
    public FOVmanip(){

	// load base configuration from ULTRACAM FOV file
	try {
	    // Create an XML document builder & transformer
	    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
	    dbf.setValidating(false);
	    _documentBuilder = dbf.newDocumentBuilder();		    
	    TransformerFactory factory = TransformerFactory.newInstance();
	    _transformer = factory.newTransformer();		    
	    
	    // Build document from base file
	    this.document = _documentBuilder.parse(FileURL.openStream());			
	} catch (SAXException e) {
	    // TODO Auto-generated catch block
	    e.printStackTrace();
	} catch (IOException e) {
	    // TODO Auto-generated catch block
	    e.printStackTrace();
	} catch (ParserConfigurationException e) {
	    // TODO Auto-generated catch block
	    e.printStackTrace();
	} catch (TransformerConfigurationException e) {
	    // TODO Auto-generated catch block
	    e.printStackTrace();
	}		
    }
    
    public String getText(){
	String text=null;
	try{
	    //initialize StreamResult with File object to save to file
	    StreamResult result = new StreamResult(new StringWriter());
	    _transformer.transform(new DOMSource(this.document), result);
	    text = result.getWriter().toString();
	}catch(Exception e){
	    e.printStackTrace();
	}
	return text;
    }
	
    
    public InputStream getStream(){
	InputStream stream = null;
	try {
	    //Transform to write out
	    
	    /*** Create Output stream from XML DOM object
	     *  and create input stream by creating byte array from output stream.
	     *  Has the disadvantage of requiring whole stream to be held in memory.
	     */
	    ByteArrayOutputStream outstr = new ByteArrayOutputStream();
	    _transformer.transform(new DOMSource(this.document),new StreamResult(outstr));
	    stream = new ByteArrayInputStream(outstr.toByteArray());
	    
	} catch (Exception e) {
	    // TODO Auto-generated catch block
	    e.printStackTrace();
	}
	return stream;
    }	
    
    public void delWindowPair(int nwin) {
	
	nodelist = this.document.getElementsByTagName("RESOURCE");
	
	// now - does this window already exist? If it doesn't there's nothing more to do.
	int i=0;
	for(i=0; i<nodelist.getLength(); i++){
	    Element elem = (Element)nodelist.item(i);
	    String check = "WindowPair"+nwin;
	    if(elem.hasAttribute("ID") && elem.getAttribute("ID").equals(check)){
		
		
		// OK, it exists, let's delete it
		daddy = elem.getParentNode();
		daddy.removeChild(elem);			
		break;
	    }
	}
    }
    
    public void addWindowPair(WindowPairs win, int nwin, Telescope tel){
	
	nodelist = this.document.getElementsByTagName("RESOURCE");
	
	// if telescope is flipped such that east is left, then take account
	double dir = 1.0;
	if (tel.flipped) dir = -1.0;
	
	//System.out.println("adding window pair " + nwin);
	
	// now - does this window already exist? If it does there's nothing more to do.
	for(int i=0; i<nodelist.getLength(); i++){
	    Element elem = (Element)nodelist.item(i);
	    String check = "WindowPair"+nwin;
	    if(elem.hasAttribute("ID") && elem.getAttribute("ID").equals(check)){
		// OK, it exists, let's bail
		//System.out.println("Window Pair " + nwin + " already exists.");
		return;
	    }
	}
	
	// OK - it doesn't exist so let's put it in!
	// get a template for a window descriptor by editing the main window resource
				
	// Get elements containing the whole FoV resource and the full CCD Resource
	Element ccdRes = null;
	Element fovRes  = null;
	for(int i=0; i<nodelist.getLength(); i++){
	    Element elem = (Element)nodelist.item(i);
	    if(elem.hasAttribute("ID") && elem.getAttribute("ID").equals("WCCD")) 
		// this is the resource element detailing the main window
		ccdRes = (Element)elem;
	    if(elem.hasAttribute("ID") && elem.getAttribute("ID").equals("UCAM_FoV")) 
		// this is the resource element detailing the whole FOV
		fovRes = (Element)elem;			
	}

	// let's try copying the ccd Resource element to a window resource element.
	Element winRes1 = null;
	winRes1 = (Element)ccdRes.cloneNode(true);
		
	// change resource name
	String id = "WindowPair"+nwin;
	winRes1.setAttribute("ID", id);
	winRes1.setAttribute("name", id);
	//change rest of attributes
	winRes1.normalize();
	nodelist = winRes1.getChildNodes();
	for(int i=0; i<nodelist.getLength(); i++){
	    if(nodelist.item(i).getNodeType() == 1){
		Element elem = (Element)nodelist.item(i);
		// description
		if(elem.getTagName().equals("DESCRIPTION")) 
		    elem.setTextContent("An ULTRACAM window");
		// Short Description
		if(elem.hasAttribute("ShortDescription"))
		    elem.setAttribute("ShortDescription","Left Window of Pair");
		if(elem.getTagName().equals("TABLE") && elem.hasAttribute("ID")){
		    elem.setAttribute("ID", "LWin");
		    elem.setAttribute("name", "Lwin");				
		}
	    }
	}
		
	// The right window of the pair is represented by a second <TABLE> node in the
	// resource, so let's copy the one that's already there
	nodelist = winRes1.getElementsByTagName("TABLE");
	Element TableElem = (Element)nodelist.item(0);
	Element rightWin = (Element)TableElem.cloneNode(true);
	rightWin.setAttribute("ID",   "RWin");
	rightWin.setAttribute("name", "RWin");
			
	winRes1.appendChild(rightWin);				
		
	// normally we set the <TD> datatags here to the 4 corners of the window
	double x1,x2,x3,x4,y1,y2;
	double PlateScale = tel.plateScale;
	double xoff = tel.delta_x;
	double yoff = tel.delta_y;
	try {
	    x1 = xoff+dir*(512-win.getXleft(nwin))*PlateScale; 
	    x3 = (x1 - dir*win.getNx(nwin)*PlateScale);
	    y1 = yoff+(win.getYstart(nwin)-512)*PlateScale; 
	    y2 = y1 + win.getNy(nwin)*PlateScale;
	    x2 = xoff+dir*(512-win.getXright(nwin))*PlateScale;
	    x4 = (x2 - dir*win.getNx(nwin)*PlateScale);
	    nodelist = winRes1.getElementsByTagName("TD");
	    nodelist.item(0).setTextContent(""+x1);
	    nodelist.item(1).setTextContent(""+y1);
	    nodelist.item(2).setTextContent(""+x3);
	    nodelist.item(3).setTextContent(""+y1);
	    nodelist.item(4).setTextContent(""+x3);
	    nodelist.item(5).setTextContent(""+y2);
	    nodelist.item(6).setTextContent(""+x1);
	    nodelist.item(7).setTextContent(""+y2);
	    nodelist.item(8).setTextContent(""+x2);
	    nodelist.item(9).setTextContent(""+y1);
	    nodelist.item(10).setTextContent(""+x4);
	    nodelist.item(11).setTextContent(""+y1);
	    nodelist.item(12).setTextContent(""+x4);
	    nodelist.item(13).setTextContent(""+y2);
	    nodelist.item(14).setTextContent(""+x2);
	    nodelist.item(15).setTextContent(""+y2);
	} catch (Exception e) {
	    e.printStackTrace();
	}
						
	//set colour to blue
	nodelist = winRes1.getElementsByTagName("PARAM");
	for(int i=0; i<nodelist.getLength(); i++){
	    Element elem = (Element)nodelist.item(i);
	    if(elem.hasAttribute("name") && (elem.getAttribute("name").equals("color")) )
		elem.setAttribute("value", "blue");
	}
		
	// append window to file
	try{
	    fovRes.appendChild(winRes1);
	} catch (DOMException e){
	    e.printStackTrace();
	}		

    }

    public void configMainWin(Telescope tel){
	/**
	   try {
	   _transformer.transform(new DOMSource(this.document), new StreamResult(System.out));
	   } catch (TransformerException e1) {
	   // TODO Auto-generated catch block
	   e1.printStackTrace();
	   }
	**/
		
	double PlateScale = tel.plateScale;
	double xoff = tel.delta_x;
	double yoff = tel.delta_y;
		
	nodelist = this.document.getElementsByTagName("RESOURCE");
	// now - does this window already exist? If it does there's nothing more to do.
	Element elem = null;
	for(int i=0; i<nodelist.getLength(); i++){
	    elem = (Element)nodelist.item(i);
	    String check = "WCCD";
	    if(elem.hasAttribute("ID") && elem.getAttribute("ID").equals(check)) break;
			
	}	

	//set colour to red
	nodelist = elem.getElementsByTagName("PARAM");
	for(int i=0; i<nodelist.getLength(); i++){
	    Element thiselem = (Element)nodelist.item(i);
	    if(thiselem.hasAttribute("name") && (thiselem.getAttribute("name").equals("color")) )
		thiselem.setAttribute("value", "red");
	}

	// normally we set the <TD> datatags here to the 4 corners of the window
	double x1,x2,y1,y2;
	x1 = 512*PlateScale+xoff; 
	y1 = 512*PlateScale+yoff;
	x2 = x1-1024*PlateScale; y2 = y1-1024*PlateScale;
	try {
	    nodelist = elem.getElementsByTagName("TD");
	    nodelist.item(0).setTextContent(""+x1);
	    nodelist.item(1).setTextContent(""+y1);
	    nodelist.item(2).setTextContent(""+x2);
	    nodelist.item(3).setTextContent(""+y1);
	    nodelist.item(4).setTextContent(""+x2);
	    nodelist.item(5).setTextContent(""+y2);
	    nodelist.item(6).setTextContent(""+x1);
	    nodelist.item(7).setTextContent(""+y2);
	} catch (Exception e) {
	    e.printStackTrace();
	}

    }	
	
    public void configWindowPair(WindowPairs win, int nwin, Telescope tel){
		
	double PlateScale = tel.plateScale;
	double dir=1.0;
	if (tel.flipped) dir = -1.0;
	double xoff = tel.delta_x;
	double yoff = tel.delta_y;
		
	nodelist = this.document.getElementsByTagName("RESOURCE");
	// now - does this window already exist? If it does there's nothing more to do.
	Element elem = null;
	for(int i=0; i<nodelist.getLength(); i++){
	    elem = (Element)nodelist.item(i);
	    String check = "WindowPair"+nwin;
	    if(elem.hasAttribute("ID") && elem.getAttribute("ID").equals(check)) break;
			
	}	

	// normally we set the <TD> datatags here to the 4 corners of the window
	double x1,x2,x3,x4,y1,y2;
	try {
	    x1 = xoff+dir*(512-win.getXleft(nwin))*PlateScale; 
	    x3 = (x1 - dir*win.getNx(nwin)*PlateScale);
	    y1 = yoff+(win.getYstart(nwin)-512)*PlateScale; 
	    y2 = y1 + win.getNy(nwin)*PlateScale;
	    x2 = xoff+dir*(512-win.getXright(nwin))*PlateScale; 
	    x4 = (x2 - dir*win.getNx(nwin)*PlateScale);
	    nodelist = elem.getElementsByTagName("TD");
	    nodelist.item(0).setTextContent(""+x1);
	    nodelist.item(1).setTextContent(""+y1);
	    nodelist.item(2).setTextContent(""+x3);
	    nodelist.item(3).setTextContent(""+y1);
	    nodelist.item(4).setTextContent(""+x3);
	    nodelist.item(5).setTextContent(""+y2);
	    nodelist.item(6).setTextContent(""+x1);
	    nodelist.item(7).setTextContent(""+y2);
	    nodelist.item(8).setTextContent(""+x2);
	    nodelist.item(9).setTextContent(""+y1);
	    nodelist.item(10).setTextContent(""+x4);
	    nodelist.item(11).setTextContent(""+y1);
	    nodelist.item(12).setTextContent(""+x4);
	    nodelist.item(13).setTextContent(""+y2);
	    nodelist.item(14).setTextContent(""+x2);
	    nodelist.item(15).setTextContent(""+y2);
	} catch (Exception e) {
	    e.printStackTrace();
	}

    }
	
    public void setCentre(String RA, String DEC){

	//String RA_old=null;
	//String DEC_old=null;
		
	nodelist = this.document.getElementsByTagName("PARAM");
	for(int i=0; i<nodelist.getLength(); i++){
	    Element elem = (Element)nodelist.item(i);
	    if(elem.hasAttribute("name")){
		String type = elem.getAttribute("name");
		if(type.equals("RA")){	
		    //RA_old = elem.getAttribute("value");
		    elem.setAttribute("value", RA);
		}
		if(type.equals("DEC")){
		    //DEC_old = elem.getAttribute("value");
		    elem.setAttribute("value", DEC);
		}
	    }
	}
		
    }

    public void setPA(String PA, Telescope tel){

	nodelist = this.document.getElementsByTagName("PARAM");
	for(int i=0; i<nodelist.getLength(); i++){
	    Element elem = (Element)nodelist.item(i);
	    if(elem.hasAttribute("name")){
		String type = elem.getAttribute("name");
		if(type.equals("PA")){	
		    //PA_old = elem.getAttribute("value");
		    elem.setAttribute("value", PA);
		}
	    }
	}
	//System.out.println("Rotation: " + PA_old);
    }	
	
}
