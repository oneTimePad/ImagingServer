package com.ruautonomous.dronecamera;

import android.os.Environment;

import com.o3dr.services.android.lib.drone.property.Parameter;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * allows client (drone app) to read images that qx service writes
 */
public class PictureStorageClient {


    File picDir;

    public PictureStorageClient(){



        //get the sd card
       final  File sdCard = Environment.getExternalStorageDirectory();
        //create the pic storage directory
        picDir = new File(sdCard.toString() + "/dronePictures");

    }

    /**
     * get the directory to read from
     * @return : File object
     */
    public File getPictureStorage(){
        return picDir;
    }
}
