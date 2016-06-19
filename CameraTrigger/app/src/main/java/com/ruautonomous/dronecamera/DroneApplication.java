package com.ruautonomous.dronecamera;

import android.app.Application;

import com.ruautonomous.dronecamera.utils.DroneTelemetry;
import com.ruautonomous.dronecamera.utils.ImageQueue;
import com.ruautonomous.dronecamera.utils.PictureStorage;

/**
 * Created by lie on 6/14/16.
 */
public class DroneApplication extends Application {

    public String server =null;
    private  QXHandler qxHandler;
    private DroneTelemetry droneTelem;
    private PictureStorage pictureStorage;
    private ImageQueue imageQueue;
    private DroneRemoteApi droneRemoteApi;
    private CameraTriggerHThread cameraTriggerHThread;
    private GroundStationHThread groundStationHThread;
    private DroneActivity context;
    private SensorTracker sensorTracker;
    private String id;


    public void setId(String id){ this.id = id;}

    public void setContext(DroneActivity context){
        this.context = context;
    }

    public void setServer(String server){
        this.server = server;
    }

    public void setSensorTracker(SensorTracker sensorTracker){
        this.sensorTracker = sensorTracker;
    }

    public void setQxHandler(QXHandler qxHandler){
        this.qxHandler = qxHandler;
    }

    public void setDroneTelemetry(DroneTelemetry droneTelem){
        this.droneTelem = droneTelem;
    }

    public void setPictureStorage(PictureStorage pictureStorage){
        this.pictureStorage = pictureStorage;
    }

    public void setDroneRemoteApi(DroneRemoteApi droneRemoteApi){
        this.droneRemoteApi = droneRemoteApi;
    }

    public void setImageQueue(ImageQueue imageQueue){
        this.imageQueue = imageQueue;
    }

    public void setCameraTriggerHThread(CameraTriggerHThread cameraTriggerHThread){
        this.cameraTriggerHThread = cameraTriggerHThread;
    }

    public void setGroundStationHThread(GroundStationHThread groundStationHThread){
        this.groundStationHThread = groundStationHThread;
    }

    public DroneActivity getContext(){return context;}

    public String getServer(){ return server;}

    public QXHandler getQxHandler(){ return qxHandler;}

    public DroneTelemetry getDroneTelem(){ return droneTelem;}

    public PictureStorage getPictureStorage(){ return  pictureStorage;}

    public DroneRemoteApi getDroneRemoteApi(){ return  droneRemoteApi;}

    public ImageQueue getImageQueue(){ return imageQueue;}

    public CameraTriggerHThread getCameraTriggerThread(){return cameraTriggerHThread;}

    public GroundStationHThread getGroundStationHThread(){return groundStationHThread;}

    public String getId(){return id;}

    public SensorTracker getSensorTracker(){return sensorTracker;}

}
