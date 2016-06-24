package com.ruautonomous.dronecamera.utils;

import android.util.AndroidException;
import android.util.Log;

import com.ruautonomous.dronecamera.DroneActivity;
import com.ruautonomous.dronecamera.SensorTracker;
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
        //SensorTracker mSensor = DroneActivity.app.getSensorTracker();
        final DroneTelemetry droneTelem = DroneActivity.app.getDroneTelem();
        //fetch data from sensors
        try {

            this.put("timeTaken", time);

            if(droneTelem.status()) {
                LatLong position = droneTelem.dronePosition();
                this.put("lat", position.getLatitude());
                this.put("lon", position.getLongitude());
                this.put("alt", droneTelem.droneAltitude());
                this.put("azimuth", droneTelem.droneYaw());
                this.put("pitch", -1 * droneTelem.dronePitch());
                this.put("roll",  -1 * droneTelem.droneRoll());
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
