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

/**
 * Wrapper for contacting drone web api
 */
public class DroneRemoteApi {
    //service ip:port pair
    public String server = null;
    public final String TAG ="DroneApi";

    //not very secure, but ok for our purposes
    private HashMap<String,String> access = new HashMap<>();
    private PictureStorageClient pictureStorageClient = new PictureStorageClient();



    /**
     * set the service to contact
     * @param server:ip:port pair
     */
    public void setServer(String server){

        this.server = "http://"+server+"/drone";
    }

    /**
     * login to django and get JWT access token
     * @param username
     * @param password
     * @throws IOException
     */
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
        if(server==null) throw new IOException("server not specified");

        try{
            //contact django to login
            responseData = SimpleHttpClient.httpPost(server+"/login",requestData,null);
            //get the access JWT
            String token = responseData.getString("token");
            access.put("accesstoken",token);

            //parse out expiration
            String[] token_split = token.split("\\.");
            String token_decode = new String(Base64.decode(token_split[1].getBytes(), Base64.DEFAULT), "UTF-8");
            JSONObject payload = new JSONObject(token_decode);
            long expiration=Long.parseLong(payload.getString("exp"));
            access.put("expiration",expiration+"");
        }
        //catch failure
        catch (JSONException e){
            Log.w(TAG,e.toString());
            throw new IOException("JSONException: "+e.toString());
        }
        //catch login failure
        catch (IOException e){
            //login failure
            if(e.toString().contains("Response Error")){
                Log.e(TAG,"Login failed");
                throw  new IOException("DroneAPI: Authorization failed");
            }
            //other error
            Log.i(TAG,"Unknown error " + e.toString());
            throw new IOException("Unknown error"+e.toString());
        }
    }

    /**
     * allow for refresh token, not implemented yet
     * @throws IOException
     */
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

    /**
     * contact django server, acts as heartbeat and posts images
     * @param id: device id
     * @param qxStatus:status of qx connection
     * @param triggering: triggering status
     * @param time: triggering delay
     * @param image: image to post
     * @return: response from server
     * @throws IOException
     */
    public JSONObject postServerContact(String id,boolean qxStatus,boolean triggering,double time,HashMap<String,Object> image) throws IOException{
                String imageName = null;
                ImageData imageData = null;
                JSONObject requestData = new JSONObject();
                JSONObject responseData = null;
                byte[] imageBytes = null;
                //is there an image to be send
                if(image!=null) {
                    //fetch image name and associated data
                    imageName = (String)image.get("pictureName") ;
                    String [] splitter = imageName.split("~");
                    imageName = splitter[0];
                    String url = splitter[1];

                    if(url.equals("FULL")){
                        try {
                            requestData.put("session", image.get("session"));
                        }
                        catch (JSONException e){
                            Log.e(TAG,e.toString());
                        }
                    }
                    imageData = (ImageData)image.get("pictureData");
                    try {
                        requestData.put("url", url);

                    }
                    catch (JSONException e){
                        Log.e(TAG,e.toString());
                    }
                    //fetch image from fs
                    File outFile = new File(pictureStorageClient.getPictureStorage(), imageName);
                    DataInputStream dataInputStream = new DataInputStream(new FileInputStream(outFile));
                    //read image at given string
                    imageBytes = new byte[(int) outFile.length()];
                    dataInputStream.readFully(imageBytes);
                    //put image data into json request
                    if(imageData!=null) {
                        Iterator<?> keys = imageData.keys();
                        while (keys.hasNext()) {
                            String key = keys.next().toString();
                            try {
                                requestData.put(key, imageData.get(key));
                            } catch (JSONException e) {
                                Log.w(TAG, "JSONException: " + e.toString());
                                throw new IOException("JSONException: " + e.toString());
                            }
                        }
                    }
                }

                try{
                    //these are always in a request regardless of whether there is an image t osend
                    requestData.put("id",id);
                    //is qx connected
                    requestData.put("qxStatus",qxStatus);
                    //is device triggering
                    if(triggering)
                        requestData.put("trigger",1);
                    else
                        requestData.put("trigger",0);
                    //triggering timeout
                    requestData.put("time",time);

                }
                catch (JSONException e) {
                    Log.w(TAG,e.toString());
                    throw  new IOException("JSONException: "+e.toString());

                }

                try{
                    //if there is an image, call image post fct
                    if(image!=null) {
                        responseData = SimpleHttpClient.httpPostPicture(server + "/serverContact", requestData, imageName, imageBytes, access);
                    }
                    //else the normal heartbeat function
                    else{
                        responseData = SimpleHttpClient.httpPost(server+"/serverContact",requestData,access);
                    }
                }
                catch (IOException e){
                    //some sort of response error
                    if(e.toString().contains("Response Error")){
                        Log.e(TAG,"Server contact failed");
                        throw  new IOException("DroneAPI: Server contact failed");
                    }
                    //token bad
                    if(e.toString().contains("Authorization")){
                        throw  new IOException("DroneAPI: tokens required");
                    }
                    //there's some sort of required argument missing
                    if(e.toString().contains("Missing")){
                        throw new IOException("DroneAPI: some arguments required arguments are null");
                    }
                    //unknown strange error
                    throw new IOException("Unknown error"+e.toString());
                }
                return  responseData;


    }




}
