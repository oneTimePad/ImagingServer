package com.ruautonomous.dronecamera;

import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import com.o3dr.android.client.ControlTower;
import com.o3dr.android.client.Drone;
import com.o3dr.android.client.interfaces.DroneListener;
import com.o3dr.android.client.interfaces.TowerListener;
import com.o3dr.services.android.lib.drone.property.Attitude;

import com.o3dr.services.android.lib.coordinate.LatLong;
import com.o3dr.services.android.lib.drone.attribute.AttributeEvent;
import com.o3dr.services.android.lib.drone.attribute.AttributeType;
import com.o3dr.services.android.lib.drone.connection.ConnectionParameter;
import com.o3dr.services.android.lib.drone.connection.ConnectionResult;
import com.o3dr.services.android.lib.drone.connection.ConnectionType;
import com.o3dr.services.android.lib.drone.property.Altitude;
import com.o3dr.services.android.lib.drone.property.Gps;


/**
 * API for accessing MAVProxy
 */
public class DroneTelemetry implements DroneListener,TowerListener {


    //dronekit and 3dr stuff
    private Drone drone;
    private ControlTower controlTower;
    private final Handler handler = new Handler();
    //telemetry connection status
    private boolean status= false;


    public DroneActivity context;
    public String TAG = "Telemtry";
    public final int UDP_PORT = 14550;
    public CharSequence connectionType ="";




    public DroneTelemetry(DroneActivity context){

        this.context = context;
        //initialize 3dr connection
        drone = new Drone();
        controlTower = new ControlTower(context);
        controlTower.connect(this);

    }

    /**
     * status of MAVProxy connection
     * @return
     */
    public boolean status(){
        return status;
    }

    public void setConnectionType(CharSequence s){
        this.connectionType = s;

    }

    /**
     * handler MAVProxy updates
     * @param event:type of update
     * @param extras
     */
    public void onDroneEvent(String event, Bundle extras){

        switch (event){
            // connected to MAVProxy
            case AttributeEvent.STATE_CONNECTED:
                status = true;
                context.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        //UI update
                        ((Button)context.findViewById(R.id.droneconnect)).setText("MAV Disconnect");
                        context.alertUser("Telemetry Connected!");
                    }
                });


                break;
            //disconnected from mavproxy
            case AttributeEvent.STATE_DISCONNECTED:
                status = false;
                context.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ((Button)context.findViewById(R.id.droneconnect)).setText("MAV Connect");

                    }
                });

                break;
            //update app UI with Drone state
            case AttributeEvent.ALTITUDE_UPDATED:
                final double altitude = droneAltitude();
                context.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ((TextView)context.findViewById(R.id.alt)).setText(String.format("%3.1f",altitude));
                    }
                });

                break;

            case AttributeEvent.ATTITUDE_UPDATED:
                context.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ((TextView)context.findViewById(R.id.roll)).setText(String.format("%3.1f",droneRoll()));
                        ((TextView)context.findViewById(R.id.pitch)).setText(String.format("%3.1f",dronePitch()));
                        ((TextView)context.findViewById(R.id.azimuth)).setText(String.format("%3.1f",droneYaw()));
                    }
                });

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

    /**
     * Connect to MAVProxy
     */
    public void connect(){
        if(!drone.isConnected()){
            //UDP connection
            if(connectionType.equals("UDP")) {
                Bundle extraParams = new Bundle();
                extraParams.putInt(ConnectionType.EXTRA_UDP_SERVER_PORT, UDP_PORT);
                ConnectionParameter connnectionParams = new ConnectionParameter(ConnectionType.TYPE_UDP, extraParams, null);
                drone.connect(connnectionParams);
            }
            //USB connection: wish this worked
            else if(connectionType.equals("USB")) {
                Bundle extraParams = new Bundle();
                extraParams.putInt(ConnectionType.EXTRA_USB_BAUD_RATE, 57600); // Set default baud rate to 57600
                ConnectionParameter connectionParams = new ConnectionParameter(ConnectionType.TYPE_USB, extraParams, null);
                drone.connect(connectionParams);
            }
            else{
                context.alertUser("Select Connection Type");
            }
            Log.i(TAG, "attempted connection");


        }
    }

    /**
     * disconnect from MAVProxy
     */
    public void disconnect(){
        if(drone.isConnected()){
            drone.disconnect();
            controlTower.unregisterDrone(drone);
            controlTower.disconnect();
            context.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ((Button)context.findViewById(R.id.droneconnect)).setText("MAV Connect");

                }
            });
            status = false;
        }


    }

    /**
     * get drone GPS
     * @return: :LatLong object
     */
    public LatLong dronePosition(){
        Gps droneGps = drone.getAttribute(AttributeType.GPS);

        return droneGps.getPosition();
    }

    /**
     * get drone altitude
     * @return: double altitude in feet
     */
    public double droneAltitude(){
        Altitude alt = drone.getAttribute(AttributeType.ALTITUDE);
        double altitude = alt.getAltitude();
        final double metersToFeet = 3.28084;
        return altitude*metersToFeet;
    }

    /**
     * get drone roll
     * @return: double roll in degrees
     */
    public double droneRoll(){
        Attitude att = drone.getAttribute(AttributeType.ATTITUDE);
        return att.getRoll();//*180/Math.PI;

    }

    /**
     * get drone pitch
     * @return: double pitch in degrees
     */
    public double dronePitch(){
        Attitude att = drone.getAttribute(AttributeType.ATTITUDE);
        return att.getPitch();//*180/Math.PI;

    }

    /**
     * get drone yaw
     * @return: double yaw in degrees
     */
    public double droneYaw(){
        Attitude att = drone.getAttribute(AttributeType.ATTITUDE);
        return att.getYaw();//*180/Math.PI;

    }


    /**
     * on MAVProxy conneciton failed handler
     * @param result: reason
     */
    public void onDroneConnectionFailed(ConnectionResult result){
        context.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                context.alertUser(" Telemetry connection failed!");
            }
        });

        Log.w(TAG,"connection failed");
    }

    /**
     * MAVProxy conneciton interrupted
     * @param errorMsg: reason
     */
    public void onDroneServiceInterrupted(String errorMsg){
        Log.w(TAG,"connection interrupted "+errorMsg);
    }

    /**
     * 3DR connection handlers
     */
    @Override
    public void onTowerConnected(){
        controlTower.registerDrone(drone,handler);
        drone.registerDroneListener(this);

        context.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                context.alertUser("3DR Services Connected!");
            }
        });
        Log.i(TAG,"3DR services connected");
    }

    @Override
    public void onTowerDisconnected(){
        Log.w(TAG,"3DR services disconnected");
    }
}
