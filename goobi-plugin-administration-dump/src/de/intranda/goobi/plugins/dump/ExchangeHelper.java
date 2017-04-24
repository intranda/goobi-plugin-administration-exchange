package de.intranda.goobi.plugins.dump;

import java.io.File;

public class ExchangeHelper {

	/**
     * public static method to count the number of files in the path and in its subdirectories
     * 
     * @param file
     */
    public static int getFilesCount(File file) {
	  File[] files = file.listFiles();
	  int count = 0;
	  for (File f : files)
	    if (f.isDirectory())
	      count += getFilesCount(f);
	    else
	      count++;

	  return count;
	}
    
//	/**
//     * public static method to replace variables in a given command and to split it into an executable runtime command
//     * 
//     * @param file
//     */
//    public static String[] commandReplace(String inCommand){
//    	String myCommand = inCommand.replaceAll("DATABASE_TEMPFILE", SQL_DUMP_PATH);
//    	return myCommand.split(", ");
//    }

}
