package com.ruautonomous.dronecamera;

import android.os.Environment;

import com.o3dr.services.android.lib.drone.property.Parameter;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Created by root on 6/25/16.
 */
public class PictureStorageClient {


    File picDir;

    public PictureStorageClient(){



        //get the sd card
       final  File sdCard = Environment.getExternalStorageDirectory();
        //create the pic storage directory
        picDir = new File(sdCard.toString() + "/dronePictures");




    }


    public File getPictureStorage(){
        return picDir;
    }
}
