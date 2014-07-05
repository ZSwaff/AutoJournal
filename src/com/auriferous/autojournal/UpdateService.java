package com.auriferous.autojournal;

import java.util.Calendar;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.location.LocationClient;

import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.Toast;

public class UpdateService extends Service implements
	GooglePlayServicesClient.ConnectionCallbacks,
	GooglePlayServicesClient.OnConnectionFailedListener{
	
    LocationClient mLocationClient;
    
    @Override
	public void onCreate() {
		super.onCreate();

		//update in more detail every hour
		Calendar cal = Calendar.getInstance();
		if(cal.get(Calendar.MINUTE)==0 || cal.get(Calendar.MINUTE)==59) {
			MainActivity.justUpdatedHourly = true;
			MainActivity.startAlarm(getApplication(), true);
		}
		
        mLocationClient = new LocationClient(this, this, this);
        mLocationClient.connect();
    }

    @Override
    public void onConnected(Bundle dataBundle) 
    {
    	Location location = mLocationClient.getLastLocation();
    	
    	Calendar cal = Calendar.getInstance();
    	boolean isGpsOn = ((LocationManager) getSystemService(LOCATION_SERVICE)).isProviderEnabled(LocationManager.GPS_PROVIDER);
        Writer.writeLocationToLog(isGpsOn, location, cal);
        Writer.updateMetadata(cal);
        
        MainActivity.justUpdated = true;
        
        mLocationClient.disconnect();
        stopSelf();
    }
    
    @Override
    public void onDisconnected() {
        Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show();
    }
    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
    	Toast.makeText(this, "Connection Failed", Toast.LENGTH_SHORT).show();
    }
    
	@Override
	public IBinder onBind(Intent intent) 
	{
		throw new UnsupportedOperationException("Not yet implemented");
	}
}
