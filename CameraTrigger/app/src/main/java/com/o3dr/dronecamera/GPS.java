package com.o3dr.dronecamera;

import android.app.AlertDialog;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;

/**
 * Created by lie on 4/30/16.
 */
public class GPS extends Service implements LocationListener {

    private Context ctx;

    boolean isGPSEnabled = false;
    boolean canGetLocation = false;

    Location location;
    double latitude;
    double longitude;


    //gps location update parameters
    private final long MIN_DISTANCE_CHANGE_FOR_UPDATES = 10; //10 meters
    private final long MIN_TIME_BW_UPDATES = 1000*60*1; //1 minute


    protected LocationManager locationManager;


    public GPS(Context context){
        this.ctx = context;
        getLocation();
    }

    public Location getLocation(){
        try {
            locationManager = (LocationManager) ctx
                    .getSystemService(LOCATION_SERVICE);

            isGPSEnabled = locationManager
                    .isProviderEnabled(LocationManager.GPS_PROVIDER);

            if (!isGPSEnabled) {
                //do something
            } else {
                this.canGetLocation = true;

                if (isGPSEnabled) {

                    if (location == null) {
                        locationManager.requestLocationUpdates(
                                LocationManager.GPS_PROVIDER,
                                MIN_TIME_BW_UPDATES,
                                MIN_DISTANCE_CHANGE_FOR_UPDATES, this);

                        Log.d("GPS Enabled", "GPS Enabled");
                        if (locationManager != null) {
                            location = locationManager
                                    .getLastKnownLocation(LocationManager.GPS_PROVIDER);
                            if (location != null) {
                                latitude = location.getLatitude();
                                longitude = location.getLongitude();
                            }
                        }


                    }

                }


            }
        }
        catch (Exception e){
            e.printStackTrace();
        }
        return location;
    }

    public void stopUsingGPS(){
        if(locationManager != null){
            locationManager.removeUpdates(GPS.this);
        }
    }

    public double getLatitude(){
        if(location!=null){
            latitude = location.getLatitude();
        }
        return latitude;
    }

    public double getLongitude(){
        if(location!=null){
            longitude =location.getLongitude();
        }
        return longitude;
    }

    public boolean canGetLocation(){
        return this.canGetLocation;
    }


    public void showSettingsAlert(){

        AlertDialog.Builder alertDialog = new AlertDialog.Builder(ctx);

        alertDialog.setTitle("GPS settings");

        alertDialog.setMessage("GPS is not enabled. Settings menu?");

        alertDialog.setPositiveButton("Settings", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                ctx.startActivity(intent);
            }
        });

        alertDialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        alertDialog.show();
    }

    @Override
    public void onLocationChanged(Location location){

    }

    @Override
    public void onProviderDisabled(String provider){

    }

    @Override
    public void onProviderEnabled(String provider){

    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras){

    }

    @Override
    public IBinder onBind(Intent arg0){
        return null;
    }
}
