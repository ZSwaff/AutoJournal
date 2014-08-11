package com.auriferous.autojournal;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;

import android.annotation.SuppressLint;
import android.location.Address;
import android.location.Location;

public class Converter
{
    @SuppressLint("SimpleDateFormat")
	public static String getCurrentTimeFormatted(Calendar cal)
    {
    	return new SimpleDateFormat("HH:mm:ss").format(cal.getTime());
    }
    public static String getCurrentDateFormatted(Calendar cal)
    {
    	return MainActivity.dayOfWeekReference[cal.get(Calendar.DAY_OF_WEEK)-1]+" "+MainActivity.monthReference[cal.get(Calendar.MONTH)].substring(3)+" "+cal.get(Calendar.DAY_OF_MONTH)+MainActivity.numberSuffixReference[cal.get(Calendar.DAY_OF_MONTH)%10]+", "+cal.get(Calendar.YEAR);
    }
    public static String timeValuesToString(int dayOM, int curHour, int curMinute)
    {
    	return (dayOM<10?"0":"")+dayOM+"-" + (curHour<10?"0":"")+curHour+":" + (curMinute<10?"0":"")+curMinute+":00";
    }
    
    public static int calculateTimeBetween(String formatedTime, int hours, int minutes)
    {
    	//seconds assumed to be 0, day assumed to be the same. returns an answer in seconds
    	int inHours = Integer.parseInt(formatedTime.substring(0, 2));
    	int inMinutes = Integer.parseInt(formatedTime.substring(3, 5));
    	int inSeconds = Integer.parseInt(formatedTime.substring(6));
    	
    	return inSeconds + 60*(inMinutes - minutes) + 3600*(inHours - hours);
    }
    public static long calculateMillisToNextInterval(Calendar cal, boolean addPadding)
    {
    	int updateIntervalMin = MainActivity.updateIntervalMin;
    	long time = (((updateIntervalMin-((cal.get(Calendar.MINUTE))%updateIntervalMin+1))*60+60-cal.get(Calendar.SECOND))*1000-cal.get(Calendar.MILLISECOND));
    	if(addPadding && time < 5000) time += MainActivity.updateIntervalMillis;
    	return time;
    }
    
    public static String intToStringNicely(int num)
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
    public static String millisToString(long millis)
    {
    	long sec = millis / 1000;
    	return (sec / 60) + ":" + (((sec%60) < 10)?"0":"") + (sec%60); 
    }
    
    @SuppressLint("DefaultLocale")
    public static String locToString(Location location)
    {
    	String latit = String.format("%.7f", location.getLatitude());
    	
    	String longit = String.format("%.7f", location.getLongitude());
    	
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
    public static Location latAndLngToLoc(double lat, double lng)
    {
    	Location loc = new Location("");
    	loc.setLongitude(lng);
    	loc.setLatitude(lat);
    	return loc;
    }
    
    public static String addressToString(Address addr)
    {
    	String returner = addr.getMaxAddressLineIndex() > 0 ? addr.getAddressLine(0) : "";
    	returner += ", " + addr.getLocality() + ", " + addr.getAdminArea();
    	String country = addr.getCountryName();
    	if(!country.equalsIgnoreCase("United States")) returner += ", " + country;
    	return returner;
    }
}