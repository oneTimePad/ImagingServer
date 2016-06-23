package com.ruautonomous.dronecamera;

import android.app.Application;
import android.util.Log;

import com.ruautonomous.dronecamera.utils.DroneTelemetry;
import com.ruautonomous.dronecamera.utils.ImageData;
import com.ruautonomous.dronecamera.utils.PictureStorage;
import com.ruautonomous.dronecamera.utils.QxRemoteApi;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by lie on 6/5/16.
 */
public class QXHandler {

    private SimpleSsdpClient mSsdpClient;
    private QxRemoteApi mRemoteApi;
    private ServerDevice mTargetServer;
    private SimpleCameraEventObserver mEventObserver;
    private String TAG = "QX";
    private final Set<String> mAvailableCameraApiSet = new HashSet<String>();
    private final Set<String> mSupportedApiSet = new HashSet<String>();
    private Application app;
    private DroneTelemetry droneTelemetry;
    private SensorTracker mSensor;
    private PictureStorage pictureStorage;
    private boolean connectionStatus = false;
    public int up = 0;



    public QXHandler(){
        this.app = DroneActivity.app;
        this.pictureStorage = DroneActivity.app.getPictureStorage();
        this.mSensor = DroneActivity.app.getSensorTracker();
        this.droneTelemetry = DroneActivity.app.getDroneTelem();




    }

    public boolean status(){ return connectionStatus;}

    public void setQXConnectionStatus(boolean connectionStatus){ this.connectionStatus = connectionStatus;}

    public void capture(){
        takeAndFetchPicture();
    }

    public void disconnect(){
        closeConnection();
    }


    public void searchQx() throws ConnectException{
        mSsdpClient = new SimpleSsdpClient();
        mSsdpClient.search(new SimpleSsdpClient.SearchResultHandler(){

            @Override
            public void onDeviceFound(final ServerDevice device){
                Log.i(TAG,"qx found");
                mTargetServer = device;
                mRemoteApi = new QxRemoteApi(mTargetServer);
                mEventObserver = new SimpleCameraEventObserver(app,mRemoteApi);
                mEventObserver.activate();
                prepareOpenConnection();
                connectionStatus = true;
                synchronized (mSsdpClient){
                    mSsdpClient.notify();
                }

            }

            @Override
            public void onFinished(){

            }

            @Override
            public void onErrorFinished(){
               Log.e(TAG,"failed to find device");
                synchronized (mSsdpClient){
                    mSsdpClient.notify();
                }
            }
        });

        try{
            synchronized (mSsdpClient) {
                mSsdpClient.wait();
            }
        }
        catch (InterruptedException e){
            Log.e(TAG,"interrupeted on wait");
        }

        if(!connectionStatus) throw new ConnectException("failed to find device");
    }

    /**
     * Retrieve a list of APIs that are available at present.
     *
     * @param replyJson
     */
    private void loadAvailableCameraApiList(JSONObject replyJson) {
        synchronized (mAvailableCameraApiSet) {
            mAvailableCameraApiSet.clear();
            try {
                JSONArray resultArrayJson = replyJson.getJSONArray("result");
                JSONArray apiListJson = resultArrayJson.getJSONArray(0);
                for (int i = 0; i < apiListJson.length(); i++) {
                    mAvailableCameraApiSet.add(apiListJson.getString(i));
                }
            } catch (JSONException e) {
                Log.w(TAG, "loadAvailableCameraApiList: JSON format error.");
            }
        }
    }

    /**
     * Retrieve a list of APIs that are supported by the target device.
     *
     * @param replyJson
     */
    private void loadSupportedApiList(JSONObject replyJson) {
        synchronized (mSupportedApiSet) {
            try {
                JSONArray resultArrayJson = replyJson.getJSONArray("results");
                for (int i = 0; i < resultArrayJson.length(); i++) {
                    mSupportedApiSet.add(resultArrayJson.getJSONArray(i).getString(0));
                }
            } catch (JSONException e) {
                Log.w(TAG, "loadSupportedApiList: JSON format error.");
            }
        }
    }

    /**
     * Check if the specified API is available at present. This works correctly
     * only for Camera API.
     *
     * @param apiName
     * @return
     */
    private boolean isCameraApiAvailable(String apiName) {
        boolean isAvailable = false;
        synchronized (mAvailableCameraApiSet) {
            isAvailable = mAvailableCameraApiSet.contains(apiName);
        }
        return isAvailable;
    }

    /**
     * Check if the version of the server is supported in this application.
     *
     * @param replyJson
     * @return
     */
    private boolean isSupportedServerVersion(JSONObject replyJson) {
        try {
            JSONArray resultArrayJson = replyJson.getJSONArray("result");
            String version = resultArrayJson.getString(1);
            String[] separated = version.split("\\.");
            int major = Integer.valueOf(separated[0]);
            if (2 <= major) {
                return true;
            }
        } catch (JSONException e) {
            Log.w(TAG, "isSupportedServerVersion: JSON format error.");
        } catch (NumberFormatException e) {
            Log.w(TAG, "isSupportedServerVersion: Number format error.");
        }
        return false;
    }

    /**
     * Check if the shoot mode is supported in this application.
     *
     * @param mode
     * @return
     */
    private boolean isSupportedShootMode(String mode) {
        if ("still".equals(mode) || "movie".equals(mode)) {
            return true;
        }
        return false;
    }

    /**
     * Check if the specified API is supported. This is for camera and avContent
     * service API. The result of this method does not change dynamically.
     *
     * @param apiName
     * @return
     */
    private boolean isApiSupported(String apiName) {
        boolean isAvailable = false;
        synchronized (mSupportedApiSet) {
            isAvailable = mSupportedApiSet.contains(apiName);
        }
        return isAvailable;
    }

    /**
     * Open connection to the camera device to start monitoring Camera events
     * and showing liveview.
     */
    private void openConnection() {

        //mEventObserver.setEventChangeListener(mEventListener);
        new Thread() {

            @Override
            public void run() {
                Log.d(TAG, "openConnection(): exec.");

                try {
                    JSONObject replyJson = null;

                    // getAvailableApiList
                    replyJson = mRemoteApi.getAvailableApiList();
                    loadAvailableCameraApiList(replyJson);

                    // check version of the server device
                    if (isCameraApiAvailable("getApplicationInfo")) {
                        Log.d(TAG, "openConnection(): getApplicationInfo()");
                        replyJson = mRemoteApi.getApplicationInfo();
                        if(!isSupportedServerVersion(replyJson)) {
                            Log.e(TAG, "Can't support this device");
                            return;
                        }



                        /*if (!isSupportedServerVersion(replyJson)) {
                            DisplayHelper.toast(getApplicationContext(), //
                                    R.string.msg_error_non_supported_device);
                            SampleCameraActivity.this.finish();
                            return;
                        }*/
                    } else {
                        // never happens;
                        return;
                    }
                    /*
                    // startRecMode if necessary.
                    if (isCameraApiAvailable("startRecMode")) {
                        Log.d(TAG, "openConnection(): startRecMode()");
                        replyJson = mRemoteApi.startRecMode();

                        // Call again.
                        replyJson = mRemoteApi.getAvailableApiList();
                        loadAvailableCameraApiList(replyJson);
                    }*/

                    // getEvent start
                    if (isCameraApiAvailable("getEvent")) {
                        Log.d(TAG, "openConnection(): EventObserver.start()");
                        mEventObserver.start();
                    }
                    /*
                    // Liveview start
                    if (isCameraApiAvailable("startLiveview")) {
                        Log.d(TAG, "openConnection(): LiveviewSurface.start()");
                        //startLiveview();
                    }*/

                    // prepare UIs
                    if (isCameraApiAvailable("getAvailableShootMode")) {
                        Log.d(TAG, "openConnection(): prepareShootModeSpinner()");
                        //prepareShootModeSpinner();
                        // Note: hide progress bar on title after this calling.

                        try {
                            replyJson = mRemoteApi.getAvailableShootMode();

                            JSONArray resultsObj = replyJson.getJSONArray("result");
                            final String currentMode = resultsObj.getString(0);
                            JSONArray availableModesJson = resultsObj.getJSONArray(1);
                            final List<String> availableModes = new ArrayList<String>();

                            for (int i = 0; i < availableModesJson.length(); i++) {
                                String mode = availableModesJson.getString(i);
                                if (!isSupportedShootMode(mode)) {
                                    mode = "";
                                }
                                availableModes.add(mode);
                            }
                            Log.i(TAG,availableModes.toString());
                        }
                        catch (IOException e){

                        }
                        catch (JSONException e){

                        }



                    }

                    /*
                    // prepare UIs
                    if (isCameraApiAvailable("actZoom")) {
                        Log.d(TAG, "openConnection(): prepareActZoomButtons()");
                        //prepareActZoomButtons(true);
                    } else {
                        //prepareActZoomButtons(false);
                    }*/

                    Log.d(TAG, "openConnection(): completed.");
                    mRemoteApi.setPostviewImageSize();
                } catch (IOException e) {
                    Log.w(TAG, "openConnection : IOException: " + e.getMessage());
                    //DisplayHelper.setProgressIndicator(SampleCameraActivity.this, false);
                    //DisplayHelper.toast(getApplicationContext(), R.string.msg_error_connection);
                }
            }
        }.start();

    }

    /**
     * Stop monitoring Camera events and close liveview connection.
     */
    private void closeConnection() {

        Log.d(TAG, "closeConnection(): exec.");
        // Liveview stop
        Log.d(TAG, "closeConnection(): LiveviewSurface.stop()");
        /*if (mLiveviewSurface != null) {
            mLiveviewSurface.stop();
            mLiveviewSurface = null;
            stopLiveview();
        }*/

        // getEvent stop
        Log.d(TAG, "closeConnection(): EventObserver.release()");
        mEventObserver.release();

        Log.d(TAG, "closeConnection(): completed.");
    }

    private static boolean isShootingStatus(String currentStatus) {
        Set<String> shootingStatus = new HashSet<String>();
        shootingStatus.add("IDLE");
        shootingStatus.add("NotReady");
        shootingStatus.add("StillCapturing");
        shootingStatus.add("StillSaving");
        shootingStatus.add("MovieWaitRecStart");
        shootingStatus.add("MovieRecording");
        shootingStatus.add("MovieWaitRecStop");
        shootingStatus.add("MovieSaving");
        shootingStatus.add("IntervalWaitRecStart");
        shootingStatus.add("IntervalRecording");
        shootingStatus.add("IntervalWaitRecStop");
        shootingStatus.add("AudioWaitRecStart");
        shootingStatus.add("AudioRecording");
        shootingStatus.add("AudioWaitRecStop");
        shootingStatus.add("AudioSaving");

        return shootingStatus.contains(currentStatus);
    }

    private void startOpenConnectionAfterChangeCameraState() {
        Log.d(TAG, "startOpenConectiontAfterChangeCameraState() exec");

        mEventObserver
                .setEventChangeListener(new SimpleCameraEventObserver.ChangeListenerTmpl() {

                    @Override
                    public void onCameraStatusChanged(String status) {
                        Log.d(TAG, "onCameraStatusChanged:" + status);
                        if ("IDLE".equals(status) || "NotReady".equals(status)) {
                            openConnection();
                        }
                        //refreshUi();
                    }

                    @Override
                    public void onShootModeChanged(String shootMode) {
                        //refreshUi();
                    }

                    @Override
                    public void onStorageIdChanged(String storageId) {
                        //refreshUi();
                    }
                });

        mEventObserver.start();


    }


    private void prepareOpenConnection() {


        new Thread() {

            @Override
            public void run() {
                try {
                    // Get supported API list (Camera API)
                    JSONObject replyJsonCamera = mRemoteApi.getCameraMethodTypes();
                    loadSupportedApiList(replyJsonCamera);

                    try {
                        // Get supported API list (AvContent API)
                        JSONObject replyJsonAvcontent = mRemoteApi.getAvcontentMethodTypes();
                        loadSupportedApiList(replyJsonAvcontent);
                    } catch (IOException e) {
                        Log.d(TAG, "AvContent is not support.");
                    }



                    if (!isApiSupported("setCameraFunction")) {

                        // this device does not support setCameraFunction.
                        // No need to check camera status.

                        openConnection();

                    } else {

                        // this device supports setCameraFunction.
                        // after confirmation of camera state, open connection.
                        Log.d(TAG, "this device support set camera function");

                        if (!isApiSupported("getEvent")) {
                            Log.e(TAG, "this device is not support getEvent");
                            openConnection();
                            return;
                        }

                        // confirm current camera status
                        String cameraStatus = null;
                        JSONObject replyJson = mRemoteApi.getEvent(false);
                        JSONArray resultsObj = replyJson.getJSONArray("result");
                        JSONObject cameraStatusObj = resultsObj.getJSONObject(1);
                        String type = cameraStatusObj.getString("type");
                        if ("cameraStatus".equals(type)) {
                            cameraStatus = cameraStatusObj.getString("cameraStatus");
                        } else {
                            throw new IOException();
                        }

                        if (isShootingStatus(cameraStatus)) {
                            Log.d(TAG, "camera function is Remote Shooting.");
                            openConnection();
                        } else {
                            // set Listener
                            startOpenConnectionAfterChangeCameraState();

                            // set Camera function to Remote Shooting
                            replyJson = mRemoteApi.setCameraFunction("Remote Shooting");
                        }
                    }
                } catch (IOException e) {
                    Log.w(TAG, "prepareToStartContentsListMode: IOException: " + e.getMessage());
                   // DisplayHelper.toast(getApplicationContext(), R.string.msg_error_api_calling);
                   // DisplayHelper.setProgressIndicator(SampleCameraActivity.this, false);
                } catch (JSONException e) {
                    Log.w(TAG, "prepareToStartContentsListMode: JSONException: " + e.getMessage());
                  //  DisplayHelper.toast(getApplicationContext(), R.string.msg_error_api_calling);
                  //  DisplayHelper.setProgressIndicator(SampleCameraActivity.this, false);
                }
            }
        }.start();
    }

    /**
     * Take a picture and retrieve the image data.
     */
    private void takeAndFetchPicture() {
        if(mRemoteApi == null) return;

        new Thread() {

            @Override
            public void run() {
                try {

                    JSONObject replyJson = mRemoteApi.actTakePicture();
                    JSONArray resultsObj = replyJson.getJSONArray("result");
                    JSONArray imageUrlsObj = resultsObj.getJSONArray(0);
                    long time = System.currentTimeMillis()/1000;
                    ImageData imageData = new ImageData(time);


                    String postImageUrl = null;
                    if (1 <= imageUrlsObj.length()) {
                        postImageUrl = imageUrlsObj.getString(0);
                    }
                    if (postImageUrl == null) {
                        Log.w(TAG, "takeAndFetchPicture: post image URL is null.");

                        return;
                    }

                    //Log.i("REAL",postImageUrl);
                    pictureStorage.writePicture(postImageUrl,imageData);
                    pictureStorage.writeLog(imageData);

                    //Log.i("IMAGE","STORED"+System.currentTimeMillis()/1000+"::"+up);
                    //up++;
                    //Log.i("CAPTUREWATCH","took "+up);




                } catch (IOException e) {
                    //Log.w(TAG, "IOException while closing slicer: " + e.getMessage());

                } catch (JSONException e) {

                    Log.w(TAG, e.toString());

                } finally {

                }
            }
        }.start();
    }





}
