package com.o3dr.dronecamera.utils;

import android.util.Log;

import com.o3dr.dronecamera.DroneActivity;
import com.o3dr.dronecamera.SensorTracker;
import com.o3dr.services.android.lib.coordinate.LatLong;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

/**
 * Created by lie on 6/7/16.
 */
//for holding image data
public class ImageData extends JSONObject {
    private float azimuth=0;
    private float pitch=0;
    private float roll=0;
    private double lat=0;
    private double lon=0;
    private double alt=0;
    private long time=0;
    public  String TAG = "imagedata";



    public ImageData(long time) throws IOException{
        SensorTracker mSensor = DroneActivity.app.getSensorTracker();
        DroneTelemetry droneTelem = DroneActivity.app.getDroneTelem();
        //fetch data from sensors
        try {
            this.put("azimuth", mSensor.getAzimuth());
            this.put("pitch", -1 * mSensor.getPitch());
            this.put("roll",  -1 * mSensor.getRoll());
            this.put("timeTaken", time);

            if(droneTelem.status()) {
                LatLong position = droneTelem.dronePosition();
                this.put("lat", position.getLatitude());
                this.put("lon", position.getLongitude());
                this.put("alt", droneTelem.droneAltitude());
            }
            else{
                this.put("lat",0);
                this.put("lon",0);
                this.put("alt",0);
            }
        }
        catch(JSONException e){
            Log.e(TAG,"failed to create pic data");
            throw new IOException("Image data creation failed");
        }

    }




}
