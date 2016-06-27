package com.ruautonomous.dronecamera.utils;

import android.util.Log;

import com.ruautonomous.dronecamera.DroneActivity;
import com.ruautonomous.dronecamera.DroneSingleton;
import com.ruautonomous.dronecamera.DroneTelemetry;

import com.o3dr.services.android.lib.coordinate.LatLong;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;


/**
 * holds image data
 */
public class ImageData extends JSONObject {
    public  String TAG = "imagedata";



    public ImageData(long time) throws IOException{

        final DroneTelemetry droneTelemetry = DroneSingleton.droneTelemetry;
        //fetch data from sensors
        try {
            //url

            //put time image was captured
            this.put("timeTaken", time);
            //attempt to get drone telemetry
            if(droneTelemetry.status()) {
                LatLong position = droneTelemetry.dronePosition();
                this.put("lat", position.getLatitude());
                this.put("lon", position.getLongitude());
                this.put("alt", droneTelemetry.droneAltitude());
                this.put("azimuth", droneTelemetry.droneYaw());
                this.put("pitch", -1 * droneTelemetry.dronePitch());
                this.put("roll",  -1 * droneTelemetry.droneRoll());
            }
            //else it's all 0
            else{
                this.put("azimuth", 0);
                this.put("pitch", 0);
                this.put("roll",  0);
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
