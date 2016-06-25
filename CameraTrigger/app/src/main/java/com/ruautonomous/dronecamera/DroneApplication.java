package com.ruautonomous.dronecamera;

import android.app.Application;

import com.ruautonomous.dronecamera.qxservices.QXCommunicationClient;

/**
 * Created by lie on 6/14/16.
 */
public class DroneApplication extends Application {

    public String server =null;

    private DroneTelemetry droneTelem;
    private LogStorage logStorage;
    private ImageQueue imageQueue;
    private DroneRemoteApi droneRemoteApi;
    private CameraTriggerHThread cameraTriggerHThread;
    private GroundStationHThread groundStationHThread;
    private DroneActivity context;
    private QXCommunicationClient qxHandler;
    private PictureStorageClient pictureStorageClient;


    private String id;


    public void setId(String id){ this.id = id;}

    public void setContext(DroneActivity context){
        this.context = context;
    }

    public void setServer(String server){
        this.server = server;
    }



    public void setQxHandler(QXCommunicationClient qxHandler){
        this.qxHandler = qxHandler;
    }

    public void setDroneTelemetry(DroneTelemetry droneTelem){
        this.droneTelem = droneTelem;
    }

    public void setLogStorage(LogStorage logStorage){
        this.logStorage = logStorage;
    }

    public void setPictureStorageClient(PictureStorageClient pictureStorageClient){
        this.pictureStorageClient = pictureStorageClient;
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

    public QXCommunicationClient getQxHandler(){ return qxHandler;}

    public DroneTelemetry getDroneTelem(){ return droneTelem;}

    public LogStorage getLogStorage(){ return  logStorage;}

    public DroneRemoteApi getDroneRemoteApi(){ return  droneRemoteApi;}

    public ImageQueue getImageQueue(){ return imageQueue;}

    public CameraTriggerHThread getCameraTriggerThread(){return cameraTriggerHThread;}

    public GroundStationHThread getGroundStationHThread(){return groundStationHThread;}

    public PictureStorageClient getPictureStorageClient(){return pictureStorageClient;}

    public String getId(){return id;}


}
