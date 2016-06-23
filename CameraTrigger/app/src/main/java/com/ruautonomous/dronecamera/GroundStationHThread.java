package com.ruautonomous.dronecamera;

import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.o3dr.android.client.Drone;
import com.o3dr.android.client.DroneApiListener;
import com.ruautonomous.dronecamera.utils.DroneTelemetry;
import com.ruautonomous.dronecamera.utils.ImageQueue;

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

        Runnable task = new Runnable() {
            @Override
            public void run() {
                try{

                    droneRemoteApi.getAccess(username,password);
                    connected = true;
                }
                catch (IOException e){
                    connected = false;
                    connect = false;

                }
                synchronized (this) {

                    this.notify();
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

                    QXHandler qxHandler = DroneActivity.app.getQxHandler();
                    boolean connectionStatus = false;
                    synchronized (qxHandler){
                        connectionStatus = qxHandler.status();
                    }
                    CameraTriggerHThread cameraTriggerHThread = DroneActivity.app.getCameraTriggerThread();
                    try {

                        synchronized (cameraTriggerHThread) {
                            response = droneRemoteApi.postServerContact(id, connectionStatus, cameraTriggerHThread.status(), cameraTriggerHThread.triggerTime, image);
                        }
                    }
                    catch (IOException e){
                        Log.e(TAG,"failed to post");
                    }


                    if (response != null && response.has("trigger")){


                        try {

                            if (Integer.parseInt(response.getString("trigger")) == 1 && response.has("time")) {
                                DroneActivity.app.getCameraTriggerThread().setTriggerTime(Double.parseDouble(response.get("time").toString()));
                                try {
                                    DroneActivity.app.getCameraTriggerThread().startCapture();
                                }
                                catch (IOException e){
                                    Log.e(TAG, "failed to start remote capture");
                                }
                            }
                            else if (Integer.parseInt(response.getString("trigger")) == 0)
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
        };
        mHandler.post(task);
        synchronized (task) {
            //waitfor login
            try {
                task.wait();
            } catch (InterruptedException e) {
                Log.e(TAG, e.toString());
            }
        }

        if(!connected) throw new ConnectException("Login Failed");
    }

}
