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

/**
 * holds images to be uploaded to api
 */
public class ImageQueue {

    //queues for picture data and picture name to upload
    //parallel queues
    private  ArrayList<ImageData> dataQueue = new ArrayList<>();
    private  ArrayList<String> pictureQueue = new ArrayList<>();


    public final String TAG = "Imagequeue";


    /**
     * push image name into queue to be fetched and uploaded
     * @param imageName : name of image in fs
     * @throws IOException
     */
    public void pushImage(String imageName) throws  IOException{
        if(imageName ==null){
            Log.w(TAG,"Required args are null");
            throw new IOException("Required args are null");
        }

        synchronized (pictureQueue) {
            pictureQueue.add(imageName);
        }
    }

    /**
     * push image data into queue while it waiting for its corresponding image to be pushed and then uploaded
     * @param imageData
     * @throws IOException
     */
    public void pushData(ImageData imageData) throws IOException{

        if(imageData ==null){
            Log.w(TAG,"Required args are null");
            throw new IOException("Required args are null");
        }

        synchronized (dataQueue) {
            dataQueue.add(imageData);
        }

    }

    /**
     * pops image and data from queues, if the corresponding image is not pushed yet, don't pop the data
     * @return: hashmap with image fs name and image data
     * @throws IndexOutOfBoundsException
     */
    public HashMap<String,Object> pop() throws IndexOutOfBoundsException{
        HashMap<String,Object> hmap = new HashMap<>();
        try {
            //check if the image has been pushed yet
            hmap.put("pictureName", pictureQueue.remove(0));
            hmap.put("pictureData", dataQueue.remove(0));
        }
        catch (IndexOutOfBoundsException e){
            //else the image has not be pushed yet, don't grab that data
            throw new IndexOutOfBoundsException("empty");
        }
        return hmap;
    }

    /**
     * status of image queue
     * @return: boolean
     */
    public boolean isEmpty(){
        return pictureQueue.isEmpty();
    }

}