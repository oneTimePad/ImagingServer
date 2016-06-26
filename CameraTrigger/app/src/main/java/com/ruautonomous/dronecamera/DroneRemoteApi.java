package com.ruautonomous.dronecamera;

import android.util.Base64;
import android.util.Log;

import com.ruautonomous.dronecamera.utils.SimpleHttpClient;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;

import com.ruautonomous.dronecamera.utils.ImageData;

public class DroneRemoteApi {

    public String server;
    public final String TAG ="DroneApi";
    //not very secure, but ok for our purposes
    private HashMap<String,String> access = new HashMap<>();
    private PictureStorageClient pictureStorage;


    public DroneRemoteApi(){
        this.server = "http://"+DroneActivity.app.getServer()+"/drone";
        this.pictureStorage = DroneActivity.app.getPictureStorageClient();

    }

    public void getAccess(String username,String password) throws IOException{

        JSONObject requestData = new JSONObject();
        JSONObject responseData = null;
        try {
            requestData.put("username", username);
            requestData.put("password", password);
        }
        catch (JSONException e){
            Log.w(TAG,e.toString());
            throw new IOException("JSONException: "+e.toString());
        }

        try{
            responseData = SimpleHttpClient.httpPost(server+"/login",requestData,null);

            String token = responseData.getString("token");
            access.put("accesstoken",token);

            //parse out expiration
            String[] token_split = token.split("\\.");
            String token_decode = new String(Base64.decode(token_split[1].getBytes(), Base64.DEFAULT), "UTF-8");
            JSONObject payload = new JSONObject(token_decode);
            long expiration=Long.parseLong(payload.getString("exp"));
            access.put("expiration",expiration+"");
        }
        catch (JSONException e){
            Log.w(TAG,e.toString());
            throw new IOException("JSONException: "+e.toString());
        }
        catch (IOException e){
            if(e.toString().contains("Response Error")){
                Log.e(TAG,"Login failed");
                throw  new IOException("DroneAPI: Authorization failed");
            }
            Log.i(TAG,"Unknown error " + e.toString());
            throw new IOException("Unknown error"+e.toString());
        }
    }

    public void refreshAccess() throws  IOException{

        JSONObject requestData = new JSONObject();
        JSONObject responseData =null;
        try {
            if(!access.containsKey("accessToken")){
                Log.w(TAG,"refresh requires a token to begin with");
                throw  new IOException("DroneAPI: refresh need an old token");
            }
            requestData.put("token", access.get("accesstoken"));
            responseData =SimpleHttpClient.httpPost(server+"/refresh",requestData,null);

            String token = responseData.getString("token");
            access.put("accesstoken",token);

            //parse out expiration
            String[] token_split = token.split("\\.");
            String token_decode = new String(Base64.decode(token_split[1].getBytes(), Base64.DEFAULT), "UTF-8");
            JSONObject payload = new JSONObject(token_decode);
            long expiration=Long.parseLong(payload.getString("exp"));
            access.put("expiration",expiration+"");
        }
        catch (JSONException e){
            Log.w(TAG,e.toString());
            throw new IOException("JSONException: "+e.toString());
        }
        catch (IOException e){
            if(e.toString().contains("Response Error")){
                Log.e(TAG,"Refresh failed");
                throw  new IOException("DroneAPI: Refresh failed");
            }
            throw new IOException("Unknown error"+e.toString());
        }

    }

    public JSONObject postServerContact(String id,boolean qxStatus,boolean triggering,double time,HashMap<String,Object> image) throws IOException{
                String imageName = null;
                ImageData imageData = null;
                JSONObject requestData = new JSONObject();
                JSONObject responseData = null;
                byte[] imageBytes = null;
                if(image!=null) {

                    imageName = (String)image.get("pictureName") ;
                    imageData = (ImageData)image.get("pictureData");

                    File outFile = new File(pictureStorage.getPictureStorage(), imageName);
                    DataInputStream dataInputStream = new DataInputStream(new FileInputStream(outFile));
                    //read image at given string
                    imageBytes = new byte[(int) outFile.length()];
                    dataInputStream.readFully(imageBytes);
                    Iterator<?> keys = imageData.keys();
                    while(keys.hasNext()){
                        String key = keys.next().toString();
                        try {
                            requestData.put(key, imageData.get(key));
                        }
                        catch (JSONException e) {
                            Log.w(TAG,"JSONException: "+e.toString());
                            throw  new IOException("JSONException: "+e.toString());
                        }
                    }
                }

                try{
                    requestData.put("id",id);
                    requestData.put("qxStatus",qxStatus);
                    if(triggering)
                        requestData.put("trigger",1);
                    else
                        requestData.put("trigger",0);
                    requestData.put("time",time);

                }
                catch (JSONException e) {
                    Log.w(TAG,e.toString());
                    throw  new IOException("JSONException: "+e.toString());

                }

                try{
                    if(image!=null) {
                        responseData = SimpleHttpClient.httpPostPicture(server + "/serverContact", requestData, imageName, imageBytes, access);
                    }
                    else{
                        responseData = SimpleHttpClient.httpPost(server+"/serverContact",requestData,access);
                    }
                }
                catch (IOException e){
                    if(e.toString().contains("Response Error")){
                        Log.e(TAG,"Server contact failed");
                        throw  new IOException("DroneAPI: Server contact failed");
                    }
                    if(e.toString().contains("Authorization")){
                        throw  new IOException("DroneAPI: tokens required");
                    }
                    if(e.toString().contains("Missing")){
                        throw new IOException("DroneAPI: some arguments required arguments are null");
                    }
                    throw new IOException("Unknown error"+e.toString());
                }
                return  responseData;


    }




}
