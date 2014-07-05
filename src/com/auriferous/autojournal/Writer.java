package com.auriferous.autojournal;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;

import android.location.Location;
import android.os.Environment;

public class Writer
{
    public static void writeToTextFile(File file, String message)
    {
    	try {
            FileWriter fileWriter = new FileWriter(file, true);
            BufferedWriter out = new BufferedWriter(fileWriter);
            out.write(message);
            out.close(); fileWriter.close();
            
        } catch (IOException e) {}
    }
    public static void writeToTextFile(File root, String fileName, String message)
    {
    	if (!root.exists())
    		root.mkdirs();
    	File file = new File(root, fileName+(fileName.contains(".txt")?"":".txt"));
        writeToTextFile(file, message);
    }
    
    public static void writeErrorToLog(String error, Calendar cal)
	{
		File baseRoot = new File(Environment.getExternalStorageDirectory(), "Location Logs");
		File yearRoot = new File(baseRoot, ""+cal.get(Calendar.YEAR));
		if (!yearRoot.exists())
			yearRoot.mkdirs();
		File monthRoot = new File(yearRoot, MainActivity.monthReference[cal.get(Calendar.MONTH)]);
		if (!monthRoot.exists())
			monthRoot.mkdirs();
		int dayOfMonth = cal.get(Calendar.DAY_OF_MONTH);
		File dayFile = new File(monthRoot, (dayOfMonth<10?"0":"")+dayOfMonth+".txt");
		String fileMetadata = (dayFile.exists()?"":Converter.getCurrentDateFormatted(cal)+"\n");
		
		writeToTextFile(dayFile, fileMetadata + "\n" + Converter.getCurrentTimeFormatted(cal) + "  ::  <" + error + ">");
	}
	public static void writeLocationToLog(Location location, Calendar cal)
	{
		File baseRoot = new File(Environment.getExternalStorageDirectory(), "Location Logs");
		File yearRoot = new File(baseRoot, ""+cal.get(Calendar.YEAR));
		if (!yearRoot.exists())
			yearRoot.mkdirs();
		File monthRoot = new File(yearRoot, MainActivity.monthReference[cal.get(Calendar.MONTH)]);
		if (!monthRoot.exists())
			monthRoot.mkdirs();
		int dayOfMonth = cal.get(Calendar.DAY_OF_MONTH);
		File dayFile = new File(monthRoot, (dayOfMonth<10?"0":"")+dayOfMonth+".txt");
		String fileMetadata = (dayFile.exists()?"":Converter.getCurrentDateFormatted(cal)+"\n");
		
		writeToTextFile(dayFile, fileMetadata + "\n" + Converter.getCurrentTimeFormatted(cal) + "  ::  " + Converter.locToString(location));
	}
	
    public static void updateMetadata(Calendar cal)
    {
    	File baseRoot = new File(Environment.getExternalStorageDirectory(), "Location Logs");
    	File metadataFile = new File(baseRoot, "Metadata.txt");
    	
    	ArrayList<String> preData = new ArrayList<String>();
    	if(metadataFile.exists()) 
    	{
    		preData = Reader.readFile(metadataFile);
    		metadataFile.delete();
    	}
    	
    	String timeNow = Converter.getCurrentTimeFormatted(cal) + " on " + Converter.getCurrentDateFormatted(cal);
    	String firstU = "First ::  "+timeNow;
    	for(String str : preData) if(str.contains("First")) firstU = str;
    	String lastU = "Last ::  "+timeNow;
    	String totalUs = "Total ::  0";
    	for(String str : preData) if(str.contains("Total")) totalUs = str;
    	totalUs = "Total ::  "+Converter.intToStringNicely((Integer.parseInt(totalUs.substring(totalUs.indexOf("::")+4).replace(",", ""))+1));
    	
    	writeToTextFile(metadataFile, firstU+"\n\n"+lastU+"\n\n"+totalUs);
    }
}