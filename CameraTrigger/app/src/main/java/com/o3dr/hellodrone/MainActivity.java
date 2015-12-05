package com.o3dr.hellodrone;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import com.o3dr.hellodrone.R;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.media.AudioManager;
import android.media.Image;
import android.net.Uri;

import android.os.Environment;
import android.os.HandlerThread;
import android.os.PowerManager;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;

import android.view.inputmethod.InputMethodManager;

import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;

import android.widget.TextView;
import android.widget.Toast;
import android.view.View;
import com.o3dr.hellodrone.SensorTracker.*;
import com.o3dr.android.client.ControlTower;
import com.o3dr.android.client.Drone;
import com.o3dr.android.client.interfaces.DroneListener;
import com.o3dr.android.client.interfaces.TowerListener;
import com.o3dr.services.android.lib.coordinate.LatLong;
import com.o3dr.services.android.lib.coordinate.LatLongAlt;
import com.o3dr.services.android.lib.drone.attribute.AttributeType;
import com.o3dr.services.android.lib.drone.connection.ConnectionParameter;
import com.o3dr.services.android.lib.drone.connection.ConnectionResult;

import com.o3dr.services.android.lib.drone.attribute.AttributeEvent;
import com.o3dr.services.android.lib.drone.connection.ConnectionType;

import com.o3dr.services.android.lib.drone.property.Altitude;
import com.o3dr.services.android.lib.drone.property.Gps;
import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowManager;
import android.view.ViewGroup.LayoutParams;

import org.w3c.dom.Text;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;


import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.locks.LockSupport;

public class MainActivity extends ActionBarActivity implements DroneListener,TowerListener {

    private SensorTracker mSensor;

    //for drone communication
    private Drone drone;

    //thread handler
    private final Handler handler = new Handler();
    //for 3dr tower
    private ControlTower controlTower;
    //for storage of pictures
    private File StoragePic;
    //for storage of logs
    private File logFile;
    //current picture
    private int picNum;
    //for uploading to ground station
    ImageUpload uploader;

    //camera
    Camera mCamera;

    //continue take pics?
    boolean on =false;

    //camera thread
    private CameraHandlerThread mThread = null;
    //upload thread
    private CameraUpload uThread = null;
    //camera trigger thread
    private CameraTakerThread tThread = null;
    //3dr pixhawk connect thread
    private ConnectThread cThread = null;
    //photomography thread
    private LocationThread lThread = null;

    //for writing to log files
    FileWriter wrt =null;
    BufferedWriter logOut = null;

    //for previewing the pic
    Preview preview;
    //this activity
    Activity act;
    //current context
    Context ctx;


    //baud rate
    private final int DEFAULT_USB_BAUD_RATE = 57600;




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //do some window stuff
        ctx = this;
        act = this;
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        super.onCreate(savedInstanceState);
        //setting the current context
        setContentView(R.layout.activity_main);

        final Context context = getApplicationContext();
        //get the drone
        drone = new Drone(context);
        //initialize picnum
        picNum = 0;

        //get the sd card
        File sdCard = Environment.getExternalStorageDirectory();
        //create the pic storage directory
        File picDir = new File(sdCard.toString() + "/picStorage");

        //create uploader
        uploader = new ImageUpload("http://1upl92.168.201.51:70/upload", picDir.toString());

       //create pic storage directory
        try {
            //if directory doesn't exist, make it
            if (!picDir.exists()) {
                picDir.mkdirs();
            }


            StoragePic = picDir;

        } catch (SecurityException e) {
            alertUser("Storage creation failed. Exiting");
            System.exit(1);

        }
        //used for setting current time log file was made
        Calendar cal = Calendar.getInstance(TimeZone.getDefault());
        String dateTime = cal.getTime().toLocaleString();

        //create the pic logs directrory
        File logDir =  new File(sdCard.toString()+"/PicLogs");


        try {

            if (!logDir.exists()) {
                logDir.mkdirs();
            }

            //create new log file
            logFile = new File(logDir,"logs "+dateTime+".txt");

        } catch (SecurityException e) {
            alertUser("Storage creation failed. Exiting");
            System.exit(1);

        }
        //3dr control tower
        controlTower = new ControlTower(getApplicationContext());
        //photomography object
        mSensor = new SensorTracker(ctx);


    }

    @Override
    public void onStart() {

        super.onStart();
        //connect to tower
        this.controlTower.connect(this);
        //for maeking interval input keyboard dissapear
        EditText time = (EditText)findViewById(R.id.time);
        final EditText time_f = time;
        //make keyboard disappear at enter
        time.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View view, int i, KeyEvent keyEvent) {
                //on enter
                if ((keyEvent.getAction() == KeyEvent.ACTION_DOWN) && i == KeyEvent.KEYCODE_ENTER) {
                    InputMethodManager mgr = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    //hide the keyboard
                    mgr.hideSoftInputFromWindow(time_f.getWindowToken(), 0);
                    return true;
                }
                return false;
            }
        });
        //for writing to the log file
        try {
            wrt = new FileWriter(logFile);
        }
        catch(IOException e){

        }
        logOut = new BufferedWriter(wrt);



    }

    @Override
    protected void onResume() {
        super.onResume();
        //open camera
        newOpenCamera();
        //create the uploader thread
        newUploaderThread();
        //create lThread
        newPhotoMogThread();
        //create the camera preview
        preview = new Preview(this, (SurfaceView) findViewById(R.id.surfaceView));

        preview.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        ((FrameLayout) findViewById(R.id.layout)).addView(preview);
        preview.setKeepScreenOn(true);
        preview.setCamera(mCamera);


        alertUser("Camera set");


    }

    @Override
    protected void onPause() {
        super.onPause();
        //only stop camera when screen goes out of focus
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);

        if (powerManager.isScreenOn()){

            return;
        }
        //tell thread to stop taking pics

        on = false;

        if(mCamera != null) {   
            preview.stopPreviewAndFreeCamera();


        }

    }

    @Override
    public void onStop() {
        super.onStop();
        try {
            logOut.close();
        }
        catch(IOException e){

        }
        //stop drone connection
        this.controlTower.disconnect();
        if (this.drone.isConnected()) {
            this.drone.disconnect();
            updateConnectedButton(false);
        }
        //tell it to stop taking pics
        on=false;
        /*
        if(mCamera!=null) {
            //preview.stopPreviewAndFreeCamera();
            //mCamera.release();
        }*/


        //finish();
    }

    //3dr tower connection
    @Override
    public void onTowerConnected() {
        alertUser("3DR Services Connected");
        this.controlTower.registerDrone(this.drone, this.handler);
        this.drone.registerDroneListener(this);
    }
    //disconnection for 3dr tower
    @Override
    public void onTowerDisconnected() {
        alertUser("3DR Service Interrupted");
    }



    //drone connection error handling
    @Override
    public void onDroneConnectionFailed(ConnectionResult result) {
        alertUser("Connection Failed:" + result.getErrorMessage());
    }

    @Override
    public void onDroneServiceInterrupted(String errorMsg) {

        alertUser(errorMsg);

    }

    //update connect button upon drone connection
    protected void updateConnectedButton(Boolean isConnected) {

        Button connectButton = (Button)findViewById(R.id.btnConnect);

        connectButton.setText(isConnected ? "Disconnect" : "Connect");

    }

    //connect to drone
    public void onBtnConnectTap(View view) {
        cThread = new ConnectThread();
        //call on connection thread
        cThread.connect();

    }
    //start taking pics
    public void onBtnTakePic(View view){
        //create pic taker thread
        tThread = new CameraTakerThread();
        //start taking pics
        tThread.capture();

    }
    //tell pic thread to stop taking pics
    public void onBtnStopPic(View view){
        on=false;
    }


    //create thread that will run camera callbacks
    private void newOpenCamera(){
        if(mThread == null){
            mThread = new CameraHandlerThread();
        }

        synchronized (mThread){
            mThread.openCamera();
        }
    }
    //create upload thread
    private void newUploaderThread(){
        if(uThread == null){
            uThread = new CameraUpload();
        }

    }
    //create lThread the photomogr thread
    private void newPhotoMogThread(){
        if(lThread==null){
            //create photomography thread
            lThread = new LocationThread(new Handler());
        }
    }

    //open the back camera
    private void oldOpenCamera(){
        try{
            alertUser("Attempt to open camera");
            //open camera
            mCamera = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK);
            Camera.Parameters params = mCamera.getParameters();
            params.setRotation(90);
        }catch(RuntimeException e){
            alertUser("Failed to open camera");
            Log.e("Camera Failed", "failed to open back camera");
        }


    }

    //get lat/lon/alt of image
    private LatLongAlt getLatLonAlt() {
        //get altitude
        Altitude droneAltitude = drone.getAttribute(AttributeType.ALTITUDE);
        if(droneAltitude==null){
            alertUser("No altitude");
            return null;
        }
        //get the altitude
        final double vehicleAltitude = droneAltitude.getAltitude();
        //update viewer with altitude
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView alt = (TextView) findViewById(R.id.altnum);
                alt.setText(vehicleAltitude + "m");
            }
        });

        //get positon
        Gps droneGps = drone.getAttribute(AttributeType.GPS);

        if(droneGps==null){
            alertUser("GPS not connected drone");
            return null;
        }
        //get lon and lat
        LatLong vehiclePosition = droneGps.getPosition();
        if(vehiclePosition==null){
            alertUser("GPS not connected vehicle");
            return null;
        }

        //get actual lat/lon
        final double latNum =vehiclePosition.getLatitude();
        final double lonNum = vehiclePosition.getLongitude();

        //update viewer with lat/lon
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView lat = (TextView) findViewById(R.id.latnum);
                TextView lon = (TextView) findViewById(R.id.lonnum);

                lat.setText(latNum + "deg");
                lon.setText(lonNum + "deg");
            }
        });


        return new LatLongAlt(vehicleAltitude, latNum, lonNum);
    }



    //callback for saved jpeg
    PictureCallback onPicTake=new PictureCallback() {
        @Override
        public void onPictureTaken ( byte[] bytes, Camera camera){
            Log.d("data size", "" + bytes.length);
            Log.d("taken", "taken");
            synchronized (uThread) {
                //send image to ground
                uThread.sendPic(bytes,StoragePic, uploader,picNum++);
            }





        }
    };
    //shutter callback
    Camera.ShutterCallback onShutter=new Camera.ShutterCallback()

    {
        @Override
        public void onShutter () {
            //make shutter sound
            AudioManager mgr = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            mgr.playSoundEffect(AudioManager.FLAG_PLAY_SOUND);


            //for holding image data
            Data dataHolder= null;
            //perform photomography and lat/lon/alt collection
            synchronized (lThread) {
                dataHolder=lThread.getDataHolder();
                try {
                    //wait for it to finish
                    lThread.join();
                }
                catch(InterruptedException e){

                }
            }




            //create log file
            try {
                String logData;
                if(dataHolder!=null) {
                    logData = "took " + picNum + " " + dataHolder.getLatLonAlt().toString();

                }
                else{
                    logData = "took " + picNum+" "+"GPS NOT CONNECTED";
                }
                logOut.write(logData);
                logOut.newLine();
            }
            catch (FileNotFoundException e){

            }
            catch (IOException e){

            }
            //send geo data to upload thread
            synchronized (uThread) {
                if(dataHolder!=null) {

                    uThread.setDataForPic(dataHolder);
                    final String tl = dataHolder.getFourCorners().TopLeft.toString();
                    final String tr = dataHolder.getFourCorners().TopRight.toString();
                    final String bl = dataHolder.getFourCorners().BottomLeft.toString();
                    final String br = dataHolder.getFourCorners().BottomRight.toString();
                    final double pitch = dataHolder.getPitch();
                    final double roll  = dataHolder.getRoll();


                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {


                            //update viewer with values
                            TextView tle = (TextView) findViewById(R.id.tle);
                            TextView tre = (TextView) findViewById(R.id.tre);
                            TextView ble = (TextView) findViewById(R.id.ble);
                            TextView bre = (TextView) findViewById(R.id.bre);
                            TextView pitche = (TextView) findViewById(R.id.pitche);
                            TextView rolle = (TextView) findViewById(R.id.rolle);

                            tle.setText(tl);
                            tre.setText(tr);
                            ble.setText(bl);
                            bre.setText(br);

                            pitche.setText(""+pitch);
                            rolle.setText(""+roll);


                        }
                    });
                }

            }


        }
    };


    //for connection to drone
    private class ConnectThread extends HandlerThread{

        Handler mHandler = null;
        ConnectThread(){
            super("ConnectThread");
            start();
            mHandler = new Handler(getLooper());
        }

        void connect(){
            mHandler.post(new Runnable(){
                public void run(){

                    //connect to drone
                    if(drone.isConnected()) {

                        drone.disconnect();
                    } else {
                        Bundle extraParams = new Bundle();
                        extraParams.putInt(ConnectionType.EXTRA_USB_BAUD_RATE, DEFAULT_USB_BAUD_RATE); // Set default baud rate to 57600
                        //connect with usb
                        ConnectionParameter connectionParams = new ConnectionParameter(ConnectionType.TYPE_USB, extraParams, null);
                        //ConnectionParameter connectionParams = new ConnectionParameter(ConnectionType.TYPE_BLUETOOTH,extraParams,null);
                        drone.connect(connectionParams);


                    }
                    /*
                    try {
                        Thread.sleep(8000);
                    }
                    catch(InterruptedException e){

                    }*/
                    //update connect button
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updateConnectedButton(drone.isConnected());
                        }
                    });


                }
            });
        }
    }


    //Thread for triggering camera
    private class CameraTakerThread extends HandlerThread{
        Handler mHandler = null;
        CameraTakerThread(){
            super("CamerTakerThread");
            start();
            mHandler = new Handler(getLooper());
        }

        void capture(){
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    //get interval input
                    EditText time = (EditText) findViewById(R.id.time);


                    Double timeNum;
                    try {
                        //parse to double
                        timeNum =Double.parseDouble(time.getText().toString());
                        if(timeNum<.65){
                            alertUser("Invalid Time Interval");
                            return;
                        }
                    } catch (NumberFormatException e) {
                        alertUser("Invalid Time Interval");
                        return;
                    }
                    //continue taking pics
                    on = true;
                    while (on) {
                        //restart viewer

                        while(-1*mSensor.getPitch()>30){
                            try {
                                Thread.sleep(1000);
                            }
                            catch(InterruptedException e){

                            }
                        }

                        mCamera.startPreview();
                        if (mCamera != null) {
                            //take pics
                            mCamera.takePicture(onShutter, null, onPicTake);


                        }
                        //wait given time interval
                        try {
                            Thread.sleep((long)(timeNum * 1000));
                        } catch (InterruptedException e) {

                        }
                    }
                }
            });
        }
    }


    //thread for opening camera and running callbacks
    private class CameraHandlerThread extends HandlerThread {
        Handler mHandler =  null;


        CameraHandlerThread(){
            super("CamerHandlerThread");
            start();
            mHandler = new Handler(getLooper());

        }
        //notfy when camera has been opened
        synchronized void notifyCameraReady(){
            notify();
        }


        void openCamera(){
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    //open camera
                    oldOpenCamera();
                    notifyCameraReady();



                }
            });
            //wait for camera to be opened
            try{
               wait();
            }
            catch(InterruptedException e){
                Log.w("Wait Failed","wait was interuppted");

            }

        }

    }

    //class for holder image four corners geo locations
    private class FourCorners{
        public LatLong TopLeft, TopRight, BottomLeft, BottomRight;


        FourCorners(LatLong tl,LatLong tr,LatLong bl,LatLong br){
            TopLeft=tl;
            TopRight=tr;
            BottomLeft=bl;
            BottomRight=br;

        }
    }


    //for holding image data
    private class Data{
        private float azimuth;
        private float pitch;
        private float roll;
        private LatLongAlt gpsData;
        private FourCorners fourC;


        Data(LatLongAlt gpsData, float azimuth, float pitch, float roll){
            this.azimuth =azimuth;
            this.pitch = pitch;
            this.roll = roll;

            this.gpsData = gpsData;

        }

        float getAzimuth(){
            return azimuth;
        }
        float getPitch(){
            return pitch;
        }
        float getRoll(){
            return roll;
        }

        LatLongAlt getLatLonAlt(){
            return gpsData;
        }

        void setFourCorners(FourCorners fc){
            this.fourC = fc;
        }

        FourCorners getFourCorners(){
            return fourC;
        }
    }

   //Thread for performing Photomography operations
    class LocationThread extends Thread implements Runnable {
        private final Handler mHandler;
        private Data dataHolder;
        LocationThread(Handler handler) {
            mHandler = handler;
        }

        void setDataHolder(){
            //get gps data
            LatLongAlt lla = getLatLonAlt();
            if(lla==null){
                return;
            }
            //create image data holder
            dataHolder=new Data(lla,mSensor.getAzimuth(),-1*mSensor.getPitch(),mSensor.getRoll());
            //get four corner geo locations
            GeotagActivity gT = new GeotagActivity(dataHolder.getLatLonAlt(),dataHolder.getAzimuth(),dataHolder.getPitch(),dataHolder.getRoll());
            //create four corners holder
            FourCorners fc = new FourCorners(gT.getTopLeft(), gT.getTopRight(),gT.getBottomLeft(),gT.getBottomRight());
            //set four corners
            dataHolder.setFourCorners(fc);

        }

        //return the create image data holder
        Data getDataHolder(){
            run();
            return dataHolder;
        }

        @Override
        public void run() {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        setDataHolder();
                    }
                });;
        }
    }

    //for uploading camera datato ground station
    private class CameraUpload extends HandlerThread{

        Handler mHandler = null;
        //image data
        Data dataForPic;


        CameraUpload(){
            super("CameraUpload");
            start();
            mHandler = new Handler(getLooper());
        }

        private void refreshGallery(File file) {
            Intent mediaScanIntent = new Intent( Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            mediaScanIntent.setData(Uri.fromFile(file));
            sendBroadcast(mediaScanIntent);
        }
        //give thread image data
        void setDataForPic(Data imageData){
            dataForPic = imageData;
        }

        //post to gcs
        synchronized void post(byte[]data, File dir, ImageUpload uploader, int picNum) {

            FileOutputStream outStream = null;

            // Write to SD Card
            try {

                //time image was saved
                Calendar cal = Calendar.getInstance(TimeZone.getDefault());
                String dateTime = cal.getTime().toLocaleString();

                //image name
                String fileName = String.format(dateTime+"%04d.jpg", picNum);

                //write image to disk
                File outFile = new File(dir, fileName);
                outStream = new FileOutputStream(outFile);
                outStream.write(data);
                outStream.close();


                refreshGallery(outFile);


                //HashMap<String, String> map = new HashMap<String, String>();
                //map.put("file_path_string", fileName);


                /*
                synchronized (uploader) {
                    try {
                       uploader.sendPostRequest(map);
                    } catch (IOException e) {
                        Log.e("Error", e.toString());
                    }
                }
                */

                }catch(FileNotFoundException e){
                    e.printStackTrace();
                }catch(IOException e){
                    e.printStackTrace();
                }



        }

        synchronized void notifyOnSuccess   (){
            notify();
        }
        //call to tell thread to send pic
        void sendPic(final byte[]data_f,final File dir_f, final ImageUpload uploader_f,final int pic_num){

            mHandler.post(new Runnable(){
                @Override
                public void run() {

                    post(data_f,dir_f,uploader_f,pic_num);
                    notifyOnSuccess();
                }
            });
        }

    }

    //used for aleting user of messages
    protected void alertUser(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
    }







































    // Drone Listener
    // ==========================================================
    //does this do anything?

    @Override
    public void onDroneEvent(String event, Bundle extras) {

        switch (event) {

            case AttributeEvent.STATE_CONNECTED:
                alertUser("Drone Connected");
                updateConnectedButton(this.drone.isConnected());

                break;

            case AttributeEvent.STATE_DISCONNECTED:
                alertUser("Drone Disconnected");
                updateConnectedButton(this.drone.isConnected());
                break;

            default:
//                Log.i("DRONE_EVENT", event); //Uncomment to see events from the drone
                break;
        }

    }



    // UI Events
    // ==========================================================






    // UI updating
    // ==========================================================

















}
