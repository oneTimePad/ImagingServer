package com.ruautonomous.dronecamera;

import com.ruautonomous.dronecamera.qxservices.QXCommunicationClient;

/**
 * access important shared objects/ never null
 */
public class DroneSingleton {

    public static DroneTelemetry droneTelemetry;
    public static ImageQueue imageQueue;
    public static QXCommunicationClient qxCommunicationClient;
    public static CameraTriggerHThread cameraTriggerHThread;
    public static GroundStationHThread groundStationHThread;

}