package com.auriferous.autojournal;

import java.util.Calendar;

import android.Manifest;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;

public class UpdateService extends Service implements
		GoogleApiClient.ConnectionCallbacks,
		GoogleApiClient.OnConnectionFailedListener {
	GoogleApiClient googleApiClient;
    
    @Override
	public void onCreate() {
		super.onCreate();

		//update in more detail every hour
		Calendar cal = Calendar.getInstance();
		if(cal.get(Calendar.MINUTE) < 4 || cal.get(Calendar.MINUTE)==59) {
			MainActivity.justUpdatedHourly = true;
			MainActivity.startAlarm(getApplication(), true);
		}
		
		boolean isAirplaneModeOn = Settings.Global.getInt(getApplicationContext().getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) == 1;
		if(isAirplaneModeOn){
	        //Writer.writeErrorToLog("airplane mode on", cal);
	        //Writer.updateMetadata(cal);
	        
	        MainActivity.justUpdated = true;
		}
		else{
			boolean isGpsOn = ((LocationManager) getSystemService(LOCATION_SERVICE)).isProviderEnabled(LocationManager.GPS_PROVIDER);
			if (!isGpsOn){
				//Writer.writeErrorToLog("gps not on", cal);
		        //Writer.updateMetadata(cal);
		        
		        MainActivity.justUpdated = true;
			}
			else{
                if (googleApiClient == null) {
                    googleApiClient = new GoogleApiClient.Builder(this)
                            .addApi(LocationServices.API)
                            .addConnectionCallbacks(this)
                            .addOnConnectionFailedListener(this)
                            .build();
                }
                googleApiClient.connect();
			}
		}
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            return;
        Location location = LocationServices.FusedLocationApi.getLastLocation(googleApiClient);
        Calendar cal = Calendar.getInstance();
        Writer.writeLocationToLog(location, cal);
        Writer.updateMetadata(cal);

        MainActivity.justUpdated = true;
        googleApiClient.disconnect();
        stopSelf();
    }
    @Override
    public void onConnectionSuspended(int code) {
        Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show();
    }
    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Toast.makeText(this, "Connection Failed", Toast.LENGTH_SHORT).show();
    }

	@Override
	public IBinder onBind(Intent intent) {
		throw new UnsupportedOperationException("Not yet implemented");
	}
}
