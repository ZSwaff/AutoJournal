package com.auriferous.autojournal;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.location.LocationClient;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.widget.Toast;

public class UpdateService extends Service implements
	GooglePlayServicesClient.ConnectionCallbacks,
	GooglePlayServicesClient.OnConnectionFailedListener{
	
    LocationClient mLocationClient;
    
    @Override
	public void onCreate() {
		super.onCreate();

		Calendar cal = Calendar.getInstance();
		if(MainActivity.conductingBatteryStatisticalAnalysis)
		{
			if(cal.get(Calendar.MINUTE)==59)
			{
				cal.set(Calendar.MINUTE,0);
				cal.set(Calendar.HOUR_OF_DAY,(cal.get(Calendar.HOUR_OF_DAY)+1)%24);
			}
			if (cal.get(Calendar.MINUTE)==0)
			{
				if(cal.get(Calendar.HOUR_OF_DAY)==0)
					MainActivity.decideWhetherThisShouldUpdateToday();
				
				writeBatteryDataToLog(cal.get(Calendar.HOUR_OF_DAY));
			}
		}
		if(cal.get(Calendar.MINUTE)==0 || cal.get(Calendar.MINUTE)==59)
			MainActivity.startAlarm(getApplication(), true);
    	
		if(!MainActivity.updatingToday) stopSelf();
		
        mLocationClient = new LocationClient(this, this, this);
        
        mLocationClient.connect();
    }
    
    public void writeLocationToLog(boolean isGPSEvenOn, Location location, Calendar cal)
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
    	String fileMetadata = (dayFile.exists()?"":getDateMetadata(cal)+"\n");
    	
    	MainActivity.writeToTextFile(dayFile, fileMetadata+"\n"+getTimeMetadata(cal)+"  ::  "+(isGPSEvenOn ? locToString(location) : " -GPS Disabled- "));
    }
    public void writeBatteryDataToLog(int hour)
    {
    	File batteryRoot = new File(new File(Environment.getExternalStorageDirectory(), "Location Logs"), "Battery Data");
    	if(!batteryRoot.exists())
    		batteryRoot.mkdirs();
    	File correctFile = new File(batteryRoot, "No Tracking.txt");
    	if(MainActivity.updatingToday)
    		correctFile = new File(batteryRoot, "Tracking.txt");
    	if(!correctFile.exists())
    	{
    		String times = "00 :: ";
    		for(int i = 1; i < 24; i++) times += "\n"+(i<10?"0":"")+i+" ::  ";
    		MainActivity.writeToTextFile(correctFile, times);
    	}
    	ArrayList<String> oldData = MainActivity.readFromFile(correctFile);
    	correctFile.delete();
    	String newData = "";
    	for(String str : oldData) 
    	{
    		newData += str;
    		if(hour==Integer.parseInt(str.substring(0, 2)))
    			newData += MainActivity.getBatteryPctg(getApplication())+",";
    		newData+="\n";
    	}
    	newData = newData.substring(0, newData.length()-1);
    	MainActivity.writeToTextFile(correctFile, newData);
    }
    public void readAndUpdateMetadata(Calendar cal)
    {
    	File baseRoot = new File(Environment.getExternalStorageDirectory(), "Location Logs");
    	File metadataFile = new File(baseRoot, "Metadata.txt");
    	
    	ArrayList<String> preData = new ArrayList<String>();
    	if(metadataFile.exists()) 
    	{
    		preData = MainActivity.readFromFile(metadataFile);
    		metadataFile.delete();
    	}
    	
    	String timeNow = getTimeMetadata(cal) + " on " + getDateMetadata(cal);
    	String firstU = "First ::  "+timeNow;
    	for(String str : preData) if(str.contains("First")) firstU = str;
    	String lastU = "Last ::  "+timeNow;
    	String totalUs = "Total ::  0";
    	for(String str : preData) if(str.contains("Total")) totalUs = str;
    	totalUs = "Total ::  "+formatIntAsString((Integer.parseInt(totalUs.substring(totalUs.indexOf("::")+4).replace(",", ""))+1));
    	
    	MainActivity.writeToTextFile(metadataFile, firstU+"\n\n"+lastU+"\n\n"+totalUs);
    }
    
    @SuppressLint("SimpleDateFormat")
	public String getTimeMetadata(Calendar cal)
    {
    	return new SimpleDateFormat("HH:mm:ss").format(cal.getTime());
    }
    public String getDateMetadata(Calendar cal)
    {
    	return MainActivity.dayOfWeekReference[cal.get(Calendar.DAY_OF_WEEK)-1]+" "+MainActivity.monthReference[cal.get(Calendar.MONTH)].substring(3)+" "+cal.get(Calendar.DAY_OF_MONTH)+MainActivity.numberSuffixReference[cal.get(Calendar.DAY_OF_MONTH)%10]+", "+cal.get(Calendar.YEAR);
    }
    
    public static String formatIntAsString(int num)
    {
    	ArrayList<Character> strNum = new ArrayList<Character>();
    	for(char c : (""+num).toCharArray()) strNum.add(c);
    	
    	String returner =strNum.get(0)+"";
    	strNum.remove(0);
    	
    	while(strNum.size()>0)
    	{
    		if(strNum.size()%3 == 0)
    			returner += ",";
    		returner += strNum.get(0);
    		strNum.remove(0);
    	}
    	
    	return returner;
    }
    public static String locToString(Location location)
    {
    	String latit = ""+location.getLatitude();
    	if(!latit.contains(".")) latit+=".0000000";
    	while(latit.substring(latit.indexOf((int)'.')+1).length()<7) latit += "0";
    	
    	String longit = ""+location.getLongitude();
    	if(!longit.contains(".")) longit+=".0000000";
    	while(longit.substring(longit.indexOf((int)'.')+1).length()<7) longit += "0";
    	
    	return latit + ", " + longit;
    }
    public static Location stringToLoc(String str)
    {
    	str = str.trim();
    	Location loc = new Location("");
    	double lat = Double.parseDouble(str.substring(0, str.indexOf(' ')-1));
    	double lng = Double.parseDouble(str.substring(str.indexOf(' ')));
    	loc.setLongitude(lng);
    	loc.setLatitude(lat);
    	return loc;
    }
    
    @Override
    public void onConnected(Bundle dataBundle) 
    {
    	Location location = mLocationClient.getLastLocation();
    	
    	Calendar cal = Calendar.getInstance();
        writeLocationToLog(((LocationManager) getSystemService(LOCATION_SERVICE)).isProviderEnabled(LocationManager.GPS_PROVIDER), location, cal);
        readAndUpdateMetadata(cal);
        
        mLocationClient.disconnect();
        stopSelf();
    }
    @Override
    public void onDisconnected() {
        Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show();
    }
    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {}
    
	@Override
	public IBinder onBind(Intent intent) 
	{
		throw new UnsupportedOperationException("Not yet implemented");
	}
}
