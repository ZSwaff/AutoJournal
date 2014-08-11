package com.auriferous.autojournal;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Scanner;

public class Reader
{
    public static ArrayList<String> readFile(File file)
    {
    	ArrayList<String> returner = new ArrayList<String>();
    	
    	try{
	    	FileReader fileReader = new FileReader(file);
	    	Scanner scanner = new Scanner(file);
	    	
	    	while (scanner.hasNextLine()) 
			    	returner.add(scanner.nextLine());
			    
			scanner.close(); fileReader.close();
    	} catch (IOException e) {}
		
    	return returner;
    }
	
    public static String findLastLoc(File baseRoot)
    {
    	boolean started = false;
    	for(int year = 2000;;year++)
    	{
    		File yearRoot = new File(baseRoot, ""+(year+1));
    		if(yearRoot.exists())
    		{
    			started = true;
    			continue;
    		}
    		if(!started) continue;
    		yearRoot = new File(baseRoot, ""+year);
    		for(int iMonth = 11; iMonth >= 0; iMonth--)
    		{
    	    	File monthRoot = new File(yearRoot, MainActivity.monthReference[iMonth]);
    	    	if(!monthRoot.exists()) continue;
    	    	for(int dayOM = 31; dayOM >= 1; dayOM--)
    	    	{
    	    		File dayFile = new File(monthRoot, (dayOM<10?"0":"")+dayOM+".txt");
        	    	if(!dayFile.exists()) continue;
        	    	ArrayList<String> dayData = readFile(dayFile);
        	    	
        	    	String lastLog = dayData.get(dayData.size()-1);
        	    	return lastLog.substring(lastLog.indexOf("::")+3);
    	    	}
    		}
    	}
    }
    
    public static String findLastError(File baseRoot)
    {
    	boolean started = false;
    	int year = 2000;
    	for(;;year++)
    	{
    		File yearRoot = new File(baseRoot, ""+(year+1));
    		if(yearRoot.exists())
    		{
    			started = true;
    			continue;
    		}
    		if(!started) continue;
    		break;
    	}
    	for(;;year--)
    	{
    		File yearRoot = new File(baseRoot, ""+year);
    		if(!yearRoot.exists()) break;
    		for(int iMonth = 11; iMonth >= 0; iMonth--)
    		{
    	    	File monthRoot = new File(yearRoot, MainActivity.monthReference[iMonth]);
    	    	if(!monthRoot.exists()) continue;
    	    	File errorLog = new File(monthRoot, "Errors.txt");
    	    	if(!errorLog.exists()) continue;
    	    	
    	    	ArrayList<String> errData = readFile(errorLog);
    	    	return errData.get(errData.size()-1);
    		}
    	}
    	return "";
    }
}