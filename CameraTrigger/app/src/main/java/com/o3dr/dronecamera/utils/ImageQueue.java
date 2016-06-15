package com.o3dr.dronecamera.utils;

import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by lie on 6/8/16.
 */
public class ImageQueue {

    //queue for pictures to uploader
    private  ArrayList<String> pictureQueue = new ArrayList<>();
    //queue for picture data to upload
    private  ArrayList<ImageData> dataQueue = new ArrayList<>();

    public final String TAG = "Imagequeue";

    public void push(String imageName,ImageData imageData) throws IOException {

        if(imageName==null || imageData ==null){
            Log.w(TAG,"Required args are null");
            throw new IOException("Required args are null");
        }

        pictureQueue.add(imageName);
        dataQueue.add(imageData);


    }

    public HashMap<String,Object> pop() throws IndexOutOfBoundsException{
        HashMap<String,Object> hmap = new HashMap<>();
        try {
            hmap.put("pictureName", pictureQueue.remove(0));
            hmap.put("pictureData", dataQueue.remove(0));
        }
        catch (IndexOutOfBoundsException e){
            throw new IndexOutOfBoundsException("empty");
        }
        return hmap;
    }
    public boolean isEmpty(){
        Log.i(TAG,"Empty");
        return pictureQueue.isEmpty();
    }

}