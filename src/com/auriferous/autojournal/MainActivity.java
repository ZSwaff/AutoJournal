package com.auriferous.autojournal;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Scanner;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Chronometer;
import android.widget.Chronometer.OnChronometerTickListener;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends FragmentActivity{
	
	public final static String[] monthReference = {"01 January", "02 February", "03 March", "04 April", "05 May", "06 June", "07 July", "08 August", "09 September", "10 October", "11 November", "12 December"};
	public final static String[] dayOfWeekReference = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
	public final static String[] numberSuffixReference = {"th", "st", "nd", "rd", "th", "th", "th", "th", "th", "th"};
	
	private final static int baseUdIntInMin = 5;
	private static int udIntInMin = baseUdIntInMin;
	public static long udInt = 1000 * 60 * udIntInMin;
    
	public final static boolean conductingBatteryStatisticalAnalysis = false;
	public static boolean updatingToday = true;
	
	public static boolean isUpdating = false;
	static PendingIntent pendAlarmIntent;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		Chronometer chron = ((Chronometer) findViewById(R.id.ancillaryChrono));
        chron.start();
        OnChronometerTickListener listener = new OnChronometerTickListener() {
			@Override
			public void onChronometerTick(Chronometer chronometer) 
			{
				chronUpdate();
			}
		};
        chron.setOnChronometerTickListener(listener);
        
        decideWhetherThisShouldUpdateToday();
        
        startAlarm(getApplication(), false);
    }
    @Override
    protected void onDestroy() {
    	((Chronometer) findViewById(R.id.ancillaryChrono)).stop();
    	
    	super.onDestroy();
    }
    
    public void startUpdates(View view)
    {
        startAlarm(getApplication(), false);
    }
    public void stopUpdates(View view)
    {
    	AlarmManager aM = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
    	aM.cancel(pendAlarmIntent);
    	
    	isUpdating=false;
    }
    public void chronUpdate()
    {
    	if(isUpdating)
    	{
	    	long time = millisToNextInterval(Calendar.getInstance(), false);
			((TextView) findViewById(R.id.nextUpdateLabel)).setText("Next update in " + millisToTime(time));
			File baseRoot = new File(Environment.getExternalStorageDirectory(), "Location Logs");
			File metadataFile = new File(baseRoot, "Metadata.txt");
    		if(metadataFile.exists())
    		{
				ArrayList<String> preData = MainActivity.readFromFile(metadataFile);
				String text = (conductingBatteryStatisticalAnalysis?"Today is a "+(updatingToday?"":"no ")+"tracking day\n":"");
				
				((TextView) findViewById(R.id.metadata)).setText(text+preData.get(0)+"\n"+preData.get(2)+"\n"+preData.get(4));
				String lastLocStr = findLastLoc(baseRoot);
				((TextView) findViewById(R.id.lastLoc)).setText("Last Loc :: "+lastLocStr /*+ "\n" + getAddress(UpdateService.stringToLoc(lastLocStr))*/);
				((TextView) findViewById(R.id.lastError)).setText("Last Error ::  "+findLastError(baseRoot));
    		}
    	}
    }
    
    public String findLastLoc(File baseRoot)
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
    	    	File monthRoot = new File(yearRoot, monthReference[iMonth]);
    	    	if(!monthRoot.exists()) continue;
    	    	for(int dayOM = 31; dayOM >= 1; dayOM--)
    	    	{
    	    		File dayFile = new File(monthRoot, (dayOM<10?"0":"")+dayOM+".txt");
        	    	if(!dayFile.exists()) continue;
        	    	ArrayList<String> dayData = readFromFile(dayFile);
        	    	
        	    	String lastLog = dayData.get(dayData.size()-1);
        	    	return lastLog.substring(lastLog.indexOf("::")+3);
    	    	}
    		}
    	}
    }
    public String findLastError(File baseRoot)
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
    	    	File monthRoot = new File(yearRoot, monthReference[iMonth]);
    	    	if(!monthRoot.exists()) continue;
    	    	File errorLog = new File(monthRoot, "Errors.txt");
    	    	if(!errorLog.exists()) return "";
    	    	
    	    	ArrayList<String> errData = readFromFile(errorLog);
    	    	return errData.get(errData.size()-1);
    		}
    	}
    }
    
    public void recalcMetadata(View view)
    {
    	File baseRoot = new File(Environment.getExternalStorageDirectory(), "Location Logs");
    	File metadataFile = new File(baseRoot, "Metadata.txt");
    	
    	if(!metadataFile.exists()) return;
    	
		metadataFile.delete();
    	
		boolean isFirst = true;
		
    	int totalUs = 0;
    	String firstU = "First ::  ";
    	String lastU = "Last ::  ";
		
    	boolean started = false;
    	for(int year = 2000;;year++)
    	{
    		File yearRoot = new File(baseRoot, ""+year);
    		if(!yearRoot.exists())
    		{
    			if(!started) continue;
    			break;
    		}
    		started = true;
    		for(String month : monthReference)
    		{
    	    	File monthRoot = new File(yearRoot, month);
    	    	if(!monthRoot.exists()) continue;
    	    	for(int dayOM = 1; dayOM <= 31; dayOM++)
    	    	{
    	    		File dayFile = new File(monthRoot, (dayOM<10?"0":"")+dayOM+".txt");
        	    	if(!dayFile.exists()) continue;
        	    	ArrayList<String> dayData = readFromFile(dayFile);
        	    	if(isFirst)
        	    	{
        	    		firstU += dayData.get(2).substring(0, 8) + " on " + dayData.get(0);
        	    		isFirst = false;
        	    	}
        	    	lastU = "Last ::  " + dayData.get(dayData.size()-1).substring(0, 8) + " on " + dayData.get(0);
        	    	int linesInFile = dayData.size();
        	    	if(linesInFile!=0) totalUs += (linesInFile-2);
    	    	}
    		}
    	}
    	
    	String strTotalUs = "Total ::  "+UpdateService.formatIntAsString(totalUs);
    	
    	writeToTextFile(metadataFile, firstU+"\n\n"+lastU+"\n\n"+strTotalUs);
    }
    public static void updateErrorFile(View view)
    {
    	Calendar cal = Calendar.getInstance();
    	updateErrorFile(view, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH));
    }
    public static void updateErrorFile(View view, int year, int monthIndex)
    {
    	//make monthly error file. display most recent on view if it's not null
    	File baseRoot = new File(Environment.getExternalStorageDirectory(), "Location Logs");
    	File yearRoot = new File(baseRoot, ""+year);
    	if (!yearRoot.exists()) return;
    	File monthRoot = new File(yearRoot, MainActivity.monthReference[monthIndex]);
    	if (!monthRoot.exists()) return;
    	File errorLog = new File(monthRoot, "Errors.txt");
        if(errorLog.exists()) errorLog.delete();
    	
        int totUpdatesMissed = 0;
        
    	ArrayList<String> errors = new ArrayList<String>();
    	String startProblem = "01-00:00:00";
    	int updatesMissed = 0;
    	boolean currentProblem = false;
    	int dayOM = 1;
    	
    	Calendar cal = Calendar.getInstance();

    	for(; dayOM <= 31; dayOM++)
    	{
        	cal.set(Calendar.YEAR, year);
    		cal.set(Calendar.MONTH, monthIndex); 
        	cal.set(Calendar.DAY_OF_MONTH, dayOM);
        	if(cal.get(Calendar.YEAR) != year || cal.get(Calendar.MONTH) != monthIndex || cal.get(Calendar.DAY_OF_MONTH) != dayOM) continue;
        	
    		File dayFile = new File(monthRoot, (dayOM<10?"0":"")+dayOM+".txt");
	    	if(!dayFile.exists()) {
	    		updatesMissed += (24*60)/5;
	    		currentProblem = true;
	    		continue;
	    	}
	    	
	    	ArrayList<String> dayData = readFromFile(dayFile); dayData.remove(0); dayData.remove(0);
	    	int curHour = 0;
	    	int curMinute = 0;
	    	String nextTime = dayData.get(0).substring(0, 8); dayData.remove(0);
	    	
	    	while(curHour < 24){
	    		int timeGap = timeBetween(nextTime, curHour, curMinute);
	    		
	    		if(timeGap <= 240){
	    			if(timeGap > -60){
		    			if(currentProblem){
		    				//a problem just ended
		    				String endTime = (dayOM<10?"0":"")+dayOM+"-" + (curHour<10?"0":"")+curHour+":" + (curMinute<10?"0":"")+curMinute+":00";
		    				String strUpdatesMissed = ""+updatesMissed;
		    				while(strUpdatesMissed.length() < 4) strUpdatesMissed = "0" + strUpdatesMissed;
		    				errors.add(strUpdatesMissed + " between " + startProblem + " and "+ endTime);
		    				totUpdatesMissed += updatesMissed;
		    				updatesMissed = 0;
		    				currentProblem = false;
		    			}
	    			}
		    		if(dayData.size() == 0) {
		    			if(curHour != 23 || curMinute != 55){
		    				updatesMissed = 12 * (23 - curHour) + (55 - curMinute)/5;
		    				startProblem = (dayOM<10?"0":"")+dayOM+"-" + (curHour<10?"0":"")+curHour+":" + (curMinute<10?"0":"")+curMinute+":00";
		    				currentProblem = true;
		    			}
		    			break;
		    		}
		    		nextTime = dayData.get(0).substring(0, 8); dayData.remove(0);
		    		if(timeGap <= -60) curMinute -=5;
	    		}
	    		else {
	    			updatesMissed++;
	    			if(!currentProblem){
	    				startProblem = (dayOM<10?"0":"")+dayOM+"-" + (curHour<10?"0":"")+curHour+":" + (curMinute<10?"0":"")+curMinute+":00";
	    				currentProblem = true;
	    			}
	    		}
	    		
	    		//errors.add(report);
	    		//advance time
	    		curMinute += 5;
	    		if(curMinute == 60){
	    			curMinute = 0;
	    			curHour++;
	    		}
	    	}
    	}
		dayOM--;

    	long curTime = Calendar.getInstance().getTimeInMillis();
    	cal = Calendar.getInstance();
    	cal.set(Calendar.YEAR, year);
		cal.set(Calendar.MONTH, monthIndex); 
    	cal.set(Calendar.DAY_OF_MONTH, dayOM);
    	long monthEndTime = cal.getTimeInMillis();
    	
    	if(currentProblem && (curTime > monthEndTime)){
			String endTime = (dayOM<10?"0":"")+dayOM+"-23:55:00";
			String strUpdatesMissed = ""+updatesMissed;
			while(strUpdatesMissed.length() < 4) strUpdatesMissed = "0" + strUpdatesMissed;
			errors.add(strUpdatesMissed + " between " + startProblem + " and "+ endTime);
			totUpdatesMissed += updatesMissed;
		}
    	
    	String allErrors = "";
    	for(String err : errors)
    		allErrors += err + "\n";
    	writeToTextFile(errorLog, "Error report for "+MainActivity.monthReference[monthIndex].substring(3)+" "+year+": "+totUpdatesMissed +" total\n\n" + allErrors);
    	
    	if(view != null){
    		
    	}
    }
    public static void updateAllErrorFiles(View view)
    {
    	boolean started = false;
    	for(int year = 2000;;year++)
    	{
    		File baseRoot = new File(Environment.getExternalStorageDirectory(), "Location Logs");
    		File yearRoot = new File(baseRoot, ""+year);
    		if(yearRoot.exists()) started = true;
    		else if(started) break;
    		
    		for(int iMonth = 0; iMonth <= 11; iMonth++)
    			updateErrorFile(view, year, iMonth);
    	}
    }
    
    public String getAddress(Location loc) {
    	if (Geocoder.isPresent()){
    		Geocoder geocoder = new Geocoder(this, Locale.getDefault());
    		List<Address> addresses = null;
    		try {
    			addresses = geocoder.getFromLocation(loc.getLatitude(), loc.getLongitude(), 1);
    		} catch (IOException e1) {
    			Log.e("LocationSampleActivity", "IO Exception in getFromLocation()");
    			e1.printStackTrace();
    			return ("IO Exception trying to get address");
    		} 

    		if (addresses != null && addresses.size() > 0)
    			return parseAddress(addresses.get(0));
    		else
    			return "No address found";
    	}
    	return "No Geocoder";
    }
    public static String parseAddress(Address addr)
    {
    	String returner = addr.getMaxAddressLineIndex() > 0 ? addr.getAddressLine(0) : "";
    	returner += ", " + addr.getLocality() + ", " + addr.getAdminArea();
    	String country = addr.getCountryName();
    	if(!country.equalsIgnoreCase("United States")) returner += ", " + country;
    	return returner;
    }

    public static int timeBetween(String formatedTime, int hours, int minutes)
    {
    	//seconds assumed to be 0, day assumed to be the same. returns an answer in seconds
    	int inHours = Integer.parseInt(formatedTime.substring(0, 2));
    	int inMinutes = Integer.parseInt(formatedTime.substring(3, 5));
    	int inSeconds = Integer.parseInt(formatedTime.substring(6));
    	
    	return inSeconds + 60*(inMinutes - minutes) + 3600*(inHours - hours);
    }
    public static long millisToNextInterval(Calendar cal, boolean addPadding)
    {
    	long time = (((udIntInMin-((cal.get(Calendar.MINUTE))%udIntInMin+1))*60+60-cal.get(Calendar.SECOND))*1000-cal.get(Calendar.MILLISECOND));
    	if(addPadding && time < 5000) time += udInt;
    	return time;
    }
    public static String millisToTime(long millis)
    {
    	long sec = millis / 1000;
    	return (sec / 60) + ":" + ((sec < 10)?"0":"") + (sec%60); 
    }

    public static void decideWhetherThisShouldUpdateToday()
    {
    	if(conductingBatteryStatisticalAnalysis)
    	{
    		Calendar cal = Calendar.getInstance();
    		Random rand = new Random(cal.get(Calendar.YEAR)*(cal.get(Calendar.MONTH)+1)*cal.get(Calendar.DAY_OF_MONTH)*(cal.get(Calendar.DAY_OF_WEEK)+1));
    		updatingToday = rand.nextBoolean();
    		if(updatingToday)
    		{
    			udIntInMin = baseUdIntInMin;
    			udInt = 1000 * 60 * udIntInMin;
    		}
    		else
    		{
    			udIntInMin = 60;
    			udInt = 1000 * 60 * udIntInMin;
    		}
    	}
    }
    public static int getBatteryPctg(Context context)
    {
    	IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
    	Intent batteryStatus = context.registerReceiver(null, ifilter);
    	
    	int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
    	int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

    	return (int) Math.round((level / (double)scale)*100d);
    }
    
    
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
    public static ArrayList<String> readFromFile(File file)
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
    
    public static void startAlarm(Context context, boolean addPadding)
    {
    	AlarmManager aM = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    	aM.cancel(pendAlarmIntent);
    	
    	Intent alarmIntent = new Intent(context, UpdateService.class);
		pendAlarmIntent = PendingIntent.getService(context, 1121, alarmIntent, 0);
		
    	long time = millisToNextInterval(Calendar.getInstance(), addPadding);
    	aM.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, time+SystemClock.elapsedRealtime(), udInt, pendAlarmIntent);
		
		double seconds = ((double)time)/1000d;
		Toast.makeText(context, "Loc logs set to update in "+ (int)seconds/60 +":"+ (seconds%60<10?"0":"") + (int)(seconds%60), Toast.LENGTH_LONG).show();
		
		isUpdating = true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.options_menu, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.recal:
                recalcMetadata(this.getCurrentFocus());
                return true;
            case R.id.error_report:
            	updateErrorFile(this.getCurrentFocus());
                return true;
            case R.id.all_error_report:
            	updateAllErrorFiles(this.getCurrentFocus());
                return true;
            case R.id.settings:
            	Toast.makeText(this.getApplicationContext(), "Unavailable Functionality", Toast.LENGTH_SHORT).show();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
