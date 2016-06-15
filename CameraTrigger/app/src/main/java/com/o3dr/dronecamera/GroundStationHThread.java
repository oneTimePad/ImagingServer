package com.o3dr.dronecamera;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.o3dr.dronecamera.utils.ImageQueue;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.ConnectException;
import java.util.HashMap;

/**
 * Created by lie on 6/14/16.
 */
public class GroundStationHThread extends HandlerThread {

    private Handler mHandler = null;
    private DroneRemoteApi droneRemoteApi;
    private ImageQueue pendingUploadImages;
    private String id;
    private boolean connect = false;
    private boolean connected = false;
    public String TAG = "GroundStationHThread";
    public double timeout = 0.0;


    GroundStationHThread(){
        super("GroundStationHThread");
        start();
        mHandler = new Handler(getLooper());
        this.droneRemoteApi = DroneActivity.app.getDroneRemoteApi();

        this.pendingUploadImages = DroneActivity.app.getImageQueue();
        this.id = DroneActivity.app.getId();

    }

    boolean status(){
        return connect;
    }

    void disconnect(){
        connect = false;
    }

    void setTimeout(double timeout) {this.timeout = timeout;}

    void connect(final String username, final String password) throws ConnectException{
        if(connect) return;
        if(timeout<=0.0)return;
        connect = true;
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                try{

                    droneRemoteApi.getAccess(username,password);
                    connected = true;
                    this.notify();
                }
                catch (IOException e){
                    connected = false;
                    this.notify();
                    return;
                }

                while (connect){
                    HashMap<String,Object> image =null;
                    try{
                      image= pendingUploadImages.pop();
                    }
                    catch (IndexOutOfBoundsException e){
                        //nothing
                    }
                    JSONObject response = null;

                        try {
                            response = droneRemoteApi.postServerContact(id, DroneActivity.app.getCameraTriggerThread().status(), image);
                        }
                        catch (IOException e){
                            Log.e(TAG,"failed to post");
                        }


                    if (response != null && response.has("trigger")){

                            try {
                                if (response.get("trigger") == 1 && response.has("time")) {
                                    DroneActivity.app.getCameraTriggerThread().setTriggerTime(Double.parseDouble(response.get("time").toString()));
                                    DroneActivity.app.getCameraTriggerThread().startCapture();
                                }
                                else if (response.get("trigger") == 0)
                                    DroneActivity.app.getCameraTriggerThread().stopCapture();
                            }
                            catch (JSONException e){
                                Log.e(TAG,e.toString());
                            }
                    }

                    try{
                        Thread.sleep((long)timeout);
                    }
                    catch (InterruptedException e){
                        Log.e(TAG, e.toString());
                    }


                }

            }
        });
        //waitfor login
        try {
            this.wait();
        }
        catch (InterruptedException e){
            Log.e(TAG,e.toString());
        }
        if(!connect) throw new ConnectException("Logn Failed");
    }

}
