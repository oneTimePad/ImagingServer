package com.ruautonomous.dronecamera;

import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;

import com.ruautonomous.dronecamera.utils.ImageData;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;


public class ImageQueue {

    //queue for picture data to upload
    private  ArrayList<ImageData> dataQueue = new ArrayList<>();
    private  ArrayList<String> pictureQueue = new ArrayList<>();


    public final String TAG = "Imagequeue";




    public void pushImage(String imageName) throws  IOException{
        if(imageName ==null){
            Log.w(TAG,"Required args are null");
            throw new IOException("Required args are null");
        }

        synchronized (pictureQueue) {
            pictureQueue.add(imageName);
        }
    }


    public void pushData(ImageData imageData) throws IOException{

        if(imageData ==null){
            Log.w(TAG,"Required args are null");
            throw new IOException("Required args are null");
        }

        synchronized (dataQueue) {
            dataQueue.add(imageData);
        }

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
        return pictureQueue.isEmpty();
    }

}