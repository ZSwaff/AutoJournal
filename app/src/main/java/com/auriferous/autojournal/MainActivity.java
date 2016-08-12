package com.auriferous.autojournal;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Chronometer;
import android.widget.Chronometer.OnChronometerTickListener;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {
	
	public final static String[] monthReference = {"01 January", "02 February", "03 March", "04 April", "05 May", "06 June", "07 July", "08 August", "09 September", "10 October", "11 November", "12 December"};
    public final static String[] pureMonthReference = {"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"};
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

        ensurePermissions();
    }
    @Override
    protected void onDestroy() {
    	((Chronometer) findViewById(R.id.ancillaryChrono)).stop();
    	
    	super.onDestroy();
    }

	public void ensurePermissions() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.RECEIVE_BOOT_COMPLETED) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.RECEIVE_BOOT_COMPLETED, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    1);
        else
            init();
	}
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 1: {
                boolean worked = grantResults.length > 0;
                for (int i = 0; i < grantResults.length; i++) {
                    if (grantResults[0] != PackageManager.PERMISSION_GRANTED)
                        worked = false;
                }
                if (worked) {
                    init();
                } else {
                    //try again, convince the user
                    ensurePermissions();
                }
            }
            default:
                break;
        }
    }
    private void init() {
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


    private void chronUpdate() {
    	long time = Converter.calculateMillisToNextInterval(Calendar.getInstance(), false);
		((TextView) findViewById(R.id.nextUpdateLabel)).setText("Sojourning in " + Converter.millisToString(time));
		if(justUpdatedHourly){
			justUpdatedHourly = false;
			Intent mServiceIntent = new Intent(getApplication(), RecalcService.class);
			mServiceIntent.setAction(RecalcService.ACTION_UPDATE_ERROR_FILE);
			getApplication().startService(mServiceIntent);
		}
		if(justUpdated){
			justUpdated = false;
			labelUpdate();
		}
    }
    private void labelUpdate() {
    	File baseRoot = new File(Environment.getExternalStorageDirectory(), "Location Logs");
		File metadataFile = new File(baseRoot, "Metadata.txt");
		if(!metadataFile.exists()) return;
		ArrayList<String> preData = Reader.readFile(metadataFile);
		TextView metadata = ((TextView) findViewById(R.id.metadata));
        String mdForm = "First record at " + Converter.shortNiceDateAndTime(Converter.parseDateAndTime(preData.get(0).substring(10))) + "\n";
        mdForm += "most recent at " + Converter.shortNiceDateAndTime(Converter.parseDateAndTime(preData.get(2).substring(9))) + "\n";
        mdForm += "for a total of " + preData.get(4).substring(10) + " logs";
        metadata.setText(mdForm);
		
		TextView lastLocTextView = ((TextView) findViewById(R.id.lastLoc));
		String newLocStr = Reader.findLastLoc(baseRoot);
		if(!newLocStr.contains("<")){
			Location newLoc = Converter.stringToLoc(newLocStr);
			String oldText = lastLocTextView.getText().toString();
			if(oldText.length() > 0 && !oldText.contains("<")){
				String oldAddr = "";
                if (oldText.split("\n").length > 2)
                    oldAddr = oldText.split("\n")[2];
				Location oldLoc = Converter.stringToLoc(oldText.split("\n")[1]);
                double distance = Math.sqrt(Math.pow(oldLoc.getLongitude() - newLoc.getLongitude(), 2) + Math.pow(oldLoc.getLatitude() - newLoc.getLatitude(), 2)) * 1000000;
				lastLocTextView.setText(("Last location was\n" + newLocStr + "\n" + ((distance < 50) ? oldAddr : getAddress(newLoc))).trim());
            } else lastLocTextView.setText(("Last location was\n" + newLocStr + "\n" + getAddress(newLoc)).trim());
        }
		else lastLocTextView.setText("Last location was\n" + newLocStr);

        //((TextView) findViewById(R.id.lastError)).setText("Last Error ::  "+Reader.findLastError(baseRoot));
    }
    
    private String getAddress(Location loc) {
    	if (Geocoder.isPresent()){
    		Geocoder geocoder = new Geocoder(this, Locale.getDefault());
    		List<Address> addresses;
    		try {
    			addresses = geocoder.getFromLocation(loc.getLatitude(), loc.getLongitude(), 1);
    		} catch (IOException e1) {
    			Log.e("LocationSampleActivity", "IO Exception in getFromLocation()");
    			e1.printStackTrace();
    			return "";
    		} 

    		if (addresses != null && addresses.size() > 0)
    			return Converter.addressToString(addresses.get(0));
    		else
    			return "";
    	}
    	return "";
    }
    public static void startAlarm(Context context, boolean addPadding) {
    	AlarmManager aM = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    	aM.cancel(pendAlarmIntent);
    	
    	Intent alarmIntent = new Intent(context, UpdateService.class);
		pendAlarmIntent = PendingIntent.getService(context, 1121, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT);
		
    	long time = Converter.calculateMillisToNextInterval(Calendar.getInstance(), addPadding);
    	aM.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, time+SystemClock.elapsedRealtime(), updateIntervalMillis, pendAlarmIntent);
		
		double seconds = ((double)time)/1000d;
		Toast.makeText(context, "Sojourning next in "+ (int)seconds/60 +":"+ (seconds%60<10?"0":"") + (int)(seconds%60), Toast.LENGTH_LONG).show();
    }
    
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.options_menu, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
		String action = null;
        switch (item.getItemId()) {
            case R.id.recal:
				action = RecalcService.ACTION_RECALC_METADATA;
				break;
            case R.id.error_report:
				action = RecalcService.ACTION_UPDATE_ERROR_FILE;
                break;
            case R.id.all_error_report:
				action = RecalcService.ACTION_UPDATE_ALL_ERROR_FILES;
				break;
//            case R.id.travel_circle:
//				action = RecalcService.ACTION_UPDATE_TRAVEL_CIRCLE;
//				break;
//            case R.id.all_travel_circle:
//				action = RecalcService.ACTION_UPDATE_ALL_TRAVEL_CIRCLES;
//				break;
            default:
                return super.onOptionsItemSelected(item);
        }
		Intent mServiceIntent = new Intent(getApplication(), RecalcService.class);
		mServiceIntent.setAction(action);
		getApplication().startService(mServiceIntent);
		return true;
    }
}
