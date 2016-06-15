package com.ruautonomous.dronecamera.utils;

import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import com.o3dr.android.client.ControlTower;
import com.o3dr.android.client.Drone;
import com.o3dr.android.client.interfaces.DroneListener;
import com.o3dr.android.client.interfaces.TowerListener;
import com.ruautonomous.dronecamera.DroneActivity;

import com.ruautonomous.dronecamera.R;
import com.o3dr.services.android.lib.coordinate.LatLong;
import com.o3dr.services.android.lib.drone.attribute.AttributeEvent;
import com.o3dr.services.android.lib.drone.attribute.AttributeType;
import com.o3dr.services.android.lib.drone.connection.ConnectionParameter;
import com.o3dr.services.android.lib.drone.connection.ConnectionResult;
import com.o3dr.services.android.lib.drone.connection.ConnectionType;
import com.o3dr.services.android.lib.drone.property.Altitude;
import com.o3dr.services.android.lib.drone.property.Gps;

import java.net.ConnectException;

/**
 * Created by lie on 6/7/16.
 */
public class DroneTelemetry implements DroneListener,TowerListener {



    private Drone drone;
    private ControlTower controlTower;
    private final Handler handler = new Handler();

    public DroneActivity context;
    public String TAG = "Telemtry";
    public final int UDP_PORT = 14550;
    private boolean status= false;


    public DroneTelemetry(){

        drone = new Drone();
        controlTower = new ControlTower(DroneActivity.app.getContext());
        this.context = DroneActivity.app.getContext();
    }

    public boolean status(){
        return status;
    }


    public void onDroneEvent(String event, Bundle extras){

        switch (event){
            case AttributeEvent.STATE_CONNECTED:
                context.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ((Button)context.findViewById(R.id.droneconnect)).setText("Disconnect");
                        context.alertUser("Connected!");
                    }
                });

                break;
            case AttributeEvent.STATE_DISCONNECTED:
                context.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ((Button)context.findViewById(R.id.droneconnect)).setText("Connect");
                    }
                });


                break;

            case AttributeEvent.ALTITUDE_UPDATED:
                final double altitude = droneAltitude();
                context.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ((TextView)context.findViewById(R.id.alt)).setText(String.format("%3.1f",altitude));
                    }
                });

                break;
            case AttributeEvent.GPS_POSITION:

                LatLong vehiclePosition = dronePosition();
                final double lat = vehiclePosition.getLatitude();
                final double lon = vehiclePosition.getLongitude();
                context.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ((TextView)context.findViewById(R.id.lat)).setText(String.format(("%3.1f"),lat));
                        ((TextView)context.findViewById(R.id.lon)).setText(String.format("%3.1f",lon));
                    }
                });

                break;
            default:
                break;



        }
    }

    public void connect() throws ConnectException{
        if(!drone.isConnected()){
            Bundle extraParams = new Bundle();
            extraParams.putInt(ConnectionType.EXTRA_UDP_SERVER_PORT, UDP_PORT);
            ConnectionParameter connnectionParams = new ConnectionParameter(ConnectionType.TYPE_UDP, extraParams, null);
            drone.connect(connnectionParams);
            Log.i(TAG, "attempted connection");
            if(!drone.isConnected()){
                throw new ConnectException("Drone Connection Failed");
            }
            Log.i(TAG,"connection successful");

        }
    }

    public void disconnect(){
        if(drone.isConnected()){
            drone.disconnect();
        }
        controlTower.unregisterDrone(drone);
        controlTower.disconnect();
    }

    public LatLong dronePosition(){
        Gps droneGps = drone.getAttribute(AttributeType.GPS);
        return droneGps.getPosition();
    }

    public double droneAltitude(){
        Altitude alt = drone.getAttribute(AttributeType.ALTITUDE);
        double altitude = alt.getAltitude();
        final double metersToFeet = 3.28084;
        return altitude*metersToFeet;
    }



    public void onDroneConnectionFailed(ConnectionResult result){
        Log.w(TAG,"connection failed");
    }

    public void onDroneServiceInterrupted(String errorMsg){
        Log.w(TAG,"connection interrupted");
    }

    @Override
    public void onTowerConnected(){
        controlTower.registerDrone(drone,handler);
        drone.registerDroneListener(this);
        Log.i(TAG,"3DR services connected");
    }

    @Override
    public void onTowerDisconnected(){
        Log.w(TAG,"3DR services disconnected");
    }
}
