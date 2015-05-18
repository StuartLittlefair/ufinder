package ufinder;

import java.io.File;
import javax.swing.filechooser.FileFilter;

/**
 * File filter to select files of the form .bmp
 * 
 */
public class FileFilterBMP extends FileFilter {
    
    public boolean accept(File file) {
	return (file.isDirectory() || file.getName().endsWith(".bmp"));
    }
    
    public String getDescription(){
	return "*.bmp";
    }
    
}
