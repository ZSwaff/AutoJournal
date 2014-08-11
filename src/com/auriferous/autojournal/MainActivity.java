package com.auriferous.autojournal;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Chronometer;
import android.widget.Chronometer.OnChronometerTickListener;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends FragmentActivity{
	
	public final static String[] monthReference = {"01 January", "02 February", "03 March", "04 April", "05 May", "06 June", "07 July", "08 August", "09 September", "10 October", "11 November", "12 December"};
	public final static String[] dayOfWeekReference = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
	public final static String[] numberSuffixReference = {"th", "st", "nd", "rd", "th", "th", "th", "th", "th", "th"};
	
	public final static int updateIntervalMin = 5;
	public static long updateIntervalMillis = updateIntervalMin * 1000 * 60;
	public static boolean justUpdated = false;
	public static boolean justUpdatedHourly = false;
	
	private static PendingIntent pendAlarmIntent;
	
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		Chronometer chron = ((Chronometer) findViewById(R.id.ancillaryChrono));
        chron.start();
        OnChronometerTickListener listener = new OnChronometerTickListener() {
			@Override
			public void onChronometerTick(Chronometer chronometer) {
				chronUpdate();
			}
		};
        chron.setOnChronometerTickListener(listener);

    	labelUpdate();
        startAlarm(getApplication(), false);
    }
    @Override
    protected void onDestroy() {
    	((Chronometer) findViewById(R.id.ancillaryChrono)).stop();
    	
    	super.onDestroy();
    }
    
    
    private void chronUpdate()
    {
    	long time = Converter.calculateMillisToNextInterval(Calendar.getInstance(), false);
		((TextView) findViewById(R.id.nextUpdateLabel)).setText("Next update in " + Converter.millisToString(time));
		if(justUpdatedHourly){
			justUpdatedHourly = false;
			updateErrorFile();
		}
		if(justUpdated){
			justUpdated = false;
			labelUpdate();
		}
    }
    private void labelUpdate()
    {
    	File baseRoot = new File(Environment.getExternalStorageDirectory(), "Location Logs");
		File metadataFile = new File(baseRoot, "Metadata.txt");
		ArrayList<String> preData = Reader.readFile(metadataFile);
		((TextView) findViewById(R.id.metadata)).setText(preData.get(0)+"\n"+preData.get(2)+"\n"+preData.get(4));
		

		TextView lastLocTextView = ((TextView) findViewById(R.id.lastLoc));
		String newLocStr = Reader.findLastLoc(baseRoot);
		if(!newLocStr.contains("<")){
			Location newLoc = Converter.stringToLoc(newLocStr);
			String oldText = lastLocTextView.getText().toString();
			if(oldText.length() > 0 && !oldText.contains("<")){
				String oldAddr = oldText.substring(oldText.indexOf("\n")+1);
				Location oldLoc = Converter.stringToLoc(oldText.substring(oldText.indexOf("::")+3, oldText.indexOf("\n")));
				double distance = Math.sqrt(Math.pow(oldLoc.getLongitude() - newLoc.getLongitude(), 2) + Math.pow(oldLoc.getLatitude() - newLoc.getLatitude(), 2)) * 1000000;
				lastLocTextView.setText("Last Loc :: " + newLocStr + "\n" + ((distance < 50)?oldAddr:getAddress(newLoc)));
			}
			else lastLocTextView.setText("Last Loc :: " + newLocStr + "\n" + getAddress(newLoc));
		}
		else lastLocTextView.setText("Last Loc :: " + newLocStr);
		
		((TextView) findViewById(R.id.lastError)).setText("Last Error ::  "+Reader.findLastError(baseRoot));
    }
    
    private void recalcMetadata()
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
        	    	ArrayList<String> dayData = Reader.readFile(dayFile);
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
    	
    	String strTotalUs = "Total ::  "+Converter.intToStringNicely(totalUs);
    	
    	Writer.writeToTextFile(metadataFile, firstU+"\n\n"+lastU+"\n\n"+strTotalUs);
    }
    
    private void updateErrorFile(int year, int monthIndex)
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
	    	
	    	ArrayList<String> dayData = Reader.readFile(dayFile); dayData.remove(0); dayData.remove(0);
	    	int curHour = 0;
	    	int curMinute = 0;
	    	String nextTime = dayData.get(0).substring(0, 8); dayData.remove(0);
	    	
	    	while(curHour < 24){
	    		int timeGap = Converter.calculateTimeBetween(nextTime, curHour, curMinute);
	    		
	    		if(timeGap <= 240){
	    			if(timeGap > -60){
		    			if(currentProblem){
		    				//a problem just ended
		    				String endTime = Converter.timeValuesToString(dayOM, curHour, curMinute);
		    				errors.add(String.format("%04d", updatesMissed) + " between " + startProblem + " and "+ endTime);
		    				totUpdatesMissed += updatesMissed;
		    				updatesMissed = 0;
		    				currentProblem = false;
		    			}
	    			}
		    		if(dayData.size() == 0) {
		    			if(curHour != 23 || curMinute != 55){
		    				updatesMissed = 12 * (23 - curHour) + (55 - curMinute)/5;
		    				startProblem = Converter.timeValuesToString(dayOM, curHour, curMinute);
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
	    				startProblem = Converter.timeValuesToString(dayOM, curHour, curMinute);
	    				currentProblem = true;
	    			}
	    		}
	    		
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
			errors.add(String.format("%04d", updatesMissed) + " between " + startProblem + " and "+ endTime);
			totUpdatesMissed += updatesMissed;
		}
    	
    	String allErrors = "";
    	for(String err : errors)
    		allErrors += err + "\n";
    	Writer.writeToTextFile(errorLog, "Error report for "+MainActivity.monthReference[monthIndex].substring(3)+" "+year+": "+totUpdatesMissed +" total\n\n" + allErrors);
    }
    private void updateErrorFile()
    {
    	Calendar cal = Calendar.getInstance();
    	updateErrorFile(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH));
    }
    private void updateAllErrorFiles()
    {
    	boolean started = false;
    	for(int year = 2000;;year++)
    	{
    		File baseRoot = new File(Environment.getExternalStorageDirectory(), "Location Logs");
    		File yearRoot = new File(baseRoot, ""+year);
    		if(yearRoot.exists()) started = true;
    		else if(started) break;
    		
    		for(int iMonth = 0; iMonth <= 11; iMonth++)
    			updateErrorFile(year, iMonth);
    	}
    }
    
    private void updateTravelCircle(int year, int monthIndex){
    	File baseRoot = new File(Environment.getExternalStorageDirectory(), "Location Logs");
    	File yearRoot = new File(baseRoot, ""+year);
    	if (!yearRoot.exists()) return;
    	File monthRoot = new File(yearRoot, MainActivity.monthReference[monthIndex]);
    	if (!monthRoot.exists()) return;
    	File travelCircleLog = new File(monthRoot, "Travel Circles.txt");
        if(travelCircleLog.exists()) travelCircleLog.delete();

        ArrayList<String> travelCircles = new ArrayList<String>();
    	Calendar cal = Calendar.getInstance();

    	for(int dayOM = 1; dayOM <= 31; dayOM++)
    	{
        	cal.set(Calendar.YEAR, year);
    		cal.set(Calendar.MONTH, monthIndex); 
        	cal.set(Calendar.DAY_OF_MONTH, dayOM);
        	if(cal.get(Calendar.YEAR) != year || cal.get(Calendar.MONTH) != monthIndex || cal.get(Calendar.DAY_OF_MONTH) != dayOM) continue;
        	
    		File dayFile = new File(monthRoot, (dayOM<10?"0":"")+dayOM+".txt");
	    	if(!dayFile.exists()) continue;
	    	
	    	ArrayList<String> fileContents = Reader.readFile(dayFile);
	    	fileContents.remove(0); fileContents.remove(0);
	    	
	    	ArrayList<Location> allLocs = new ArrayList<Location>();
	    	for(String line : fileContents){
	    		String locStr = line.split(" :: ")[1].trim();
	    		allLocs.add(Converter.stringToLoc(locStr));
	    	}
	    	
	    	Area dayTravelCircle = new Area("", allLocs);
	    	travelCircles.add(dayOM + "  ::" + dayTravelCircle.toString());
    	}
        
    	String allCircles = "";
    	for(String circ : travelCircles)
    		allCircles += circ + "\n";
    	Writer.writeToTextFile(travelCircleLog, "Travel circle report for "+MainActivity.monthReference[monthIndex].substring(3)+" "+year+"\n\n" + allCircles);
    
    }
	private void updateTravelCircle(){
    	Calendar cal = Calendar.getInstance();
    	updateTravelCircle(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH));
    }
    private void updateAllTravelCircles(){
    	boolean started = false;
    	for(int year = 2000;;year++)
    	{
    		File baseRoot = new File(Environment.getExternalStorageDirectory(), "Location Logs");
    		File yearRoot = new File(baseRoot, ""+year);
    		if(yearRoot.exists()) started = true;
    		else if(started) break;
    		
    		for(int iMonth = 0; iMonth <= 11; iMonth++)
    			updateTravelCircle(year, iMonth);
    	}
    }
    
    private String getAddress(Location loc) {
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
    			return Converter.addressToString(addresses.get(0));
    		else
    			return "No address found";
    	}
    	return "No Geocoder";
    }
    public static void startAlarm(Context context, boolean addPadding)
    {
    	AlarmManager aM = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    	aM.cancel(pendAlarmIntent);
    	
    	Intent alarmIntent = new Intent(context, UpdateService.class);
		pendAlarmIntent = PendingIntent.getService(context, 1121, alarmIntent, 0);
		
    	long time = Converter.calculateMillisToNextInterval(Calendar.getInstance(), addPadding);
    	aM.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, time+SystemClock.elapsedRealtime(), updateIntervalMillis, pendAlarmIntent);
		
		double seconds = ((double)time)/1000d;
		Toast.makeText(context, "AutoJournal will next record in "+ (int)seconds/60 +":"+ (seconds%60<10?"0":"") + (int)(seconds%60), Toast.LENGTH_LONG).show();
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
                recalcMetadata();
                labelUpdate();
                return true;
            case R.id.error_report:
            	updateErrorFile();
                labelUpdate();
                return true;
            case R.id.all_error_report:
            	updateAllErrorFiles();
                labelUpdate();
                return true;
            case R.id.travel_circle:
            	updateTravelCircle();
                return true;
            case R.id.all_travel_circle:
            	updateAllTravelCircles();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
