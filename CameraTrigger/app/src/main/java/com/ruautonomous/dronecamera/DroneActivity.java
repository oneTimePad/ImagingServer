package com.ruautonomous.dronecamera;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;

import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.provider.Settings;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;

import com.o3dr.android.client.Drone;
import com.ruautonomous.dronecamera.qxservices.QXCommunicationClient;

import android.view.Window;
import android.view.WindowManager;

import java.io.IOException;
import java.net.ConnectException;


public class DroneActivity extends ActionBarActivity {








    //application components, important objects
    public static String server =null;
    private  DroneTelemetry droneTelemetry;
    private  ImageQueue imageQueue;
    private  QXCommunicationClient qxCommunicationClient;
    private  CameraTriggerHThread cameraTriggerThread ;
    private  GroundStationHThread groundStationHThread;

    public final double SERVER_TIMEOUT = 1000;
    public final String TAG = "Main";
    public int manualTriggerTime = 0;
    public boolean accelUpdate = true;
    public ProgressDialog searching;

    public String size = "2M";




    @Override
    protected void onCreate(Bundle savedInstanceState) {

        droneTelemetry = new DroneTelemetry(DroneActivity.this);
        imageQueue = new ImageQueue();
        qxCommunicationClient = new QXCommunicationClient(DroneActivity.this);
        cameraTriggerThread = new CameraTriggerHThread(DroneActivity.this);
        groundStationHThread= new GroundStationHThread( Settings.Secure.getString(getApplicationContext().getContentResolver(),Settings.Secure.ANDROID_ID),DroneActivity.this);



        DroneSingleton.imageQueue=imageQueue;
        DroneSingleton.droneTelemetry =droneTelemetry;
        DroneSingleton.cameraTriggerHThread = cameraTriggerThread;
        DroneSingleton.groundStationHThread = groundStationHThread;
        DroneSingleton.qxCommunicationClient = qxCommunicationClient;

        groundStationHThread.set();
        cameraTriggerThread.set();
        qxCommunicationClient.set();

        //settings up windows
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);


        //Here is the important stuff
        super.onCreate(savedInstanceState);

        //set layout
        setContentView(R.layout.activity_main);

        //synchronize time with ground station
        final Resources res= this.getResources();
        final int id = Resources.getSystem().getIdentifier("config_ntpServer","string","android");
        String defaultServer = res.getString(id);
        Log.i("NTP",defaultServer);



        //set process NIC to Ethernet
        ethernet(null);


    }


    /**
    *Sets process NIC to Ethernet
     * @param v: button that runs this function
    **/
    public void ethernet(View v){
        //initialize connectivity manager
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        Network etherNetwork = null;
        //search for ethernet NIC
        for (Network network : connectivityManager.getAllNetworks()) {
            NetworkInfo networkInfo = connectivityManager.getNetworkInfo(network);
            if (networkInfo.getType() == ConnectivityManager.TYPE_ETHERNET) {
                etherNetwork = network;
                alertUser("FOUND ETHERNET!");
            }
        }
        //if found, bound to it for this process
        Network boundNetwork = connectivityManager.getBoundNetworkForProcess();
        if (boundNetwork != null) {
            NetworkInfo boundNetworkInfo = connectivityManager.getNetworkInfo(boundNetwork);
            if (boundNetworkInfo.getType() != ConnectivityManager.TYPE_ETHERNET) {
                if (etherNetwork != null) {
                    connectivityManager.bindProcessToNetwork(etherNetwork);

                }
            }
        }
        //sometimes the top loop doesn't work, so we add this
        if(etherNetwork!=null)
            connectivityManager.bindProcessToNetwork(etherNetwork);

    }


    /**
     * searches for QX devices on Wi-Fi network
     * awares user of QX search
    **/
    public void searchQx(){
        //we need the qx IPC handler to do this
        if(qxCommunicationClient== null)return;

        //show the progress bar to user
        searching= ProgressDialog.show(this,"","Searching for QX device...",true);
        searching.setCancelable(false);

        //run the search of the Wi-Fi network
       new Thread(new Runnable() {
            @Override
            public void run() {
                //this is asynchronous, the response will come later in setSearchQxStatus
                qxCommunicationClient.search(size);

            }
        }).start();

    }

    /**
     *asynchronous callback for QX device search, alert user of status
     * @param status: response from the Qx service when a device was found,
     */
    public void setSearchQxStatus(boolean status){
        //sanity check
        if(searching==null) return;

        //dismiss the loading screen
        searching.dismiss();

        //if connected
        if(status){
            new AlertDialog.Builder(DroneActivity.this)
                    .setMessage("Found QX device!").show();



        }
        //else not connected
        else{
            new AlertDialog.Builder(DroneActivity.this)
                    .setMessage("Failed to find QX device!").show();
        }


    }

    public void setGCSConnectionStatus(boolean status){

        if(status){
            new AlertDialog.Builder(DroneActivity.this)
                    .setMessage("Connection Successful!").show();
            ((Button)findViewById(R.id.remote)).setText(R.string.gcsdisconnect);
        }

        else{
            new AlertDialog.Builder(DroneActivity.this)
                    .setMessage("Connection Failed!").show();

        }

    }

    /**
     * helper function for allowing keyboard to be dismissed on completition of input
     * @param v: the view to enable this on
     **/
    private void hideKeyBoard(final View v){
        v.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View view, int i, KeyEvent keyEvent) {
                //on enter
                if ((keyEvent.getAction() == KeyEvent.ACTION_DOWN) && i == KeyEvent.KEYCODE_ENTER) {
                    InputMethodManager mgr = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    //hide the keyboard
                    mgr.hideSoftInputFromWindow(v.getWindowToken(), 0);
                    return true;
                }
                return false;
            }
        });
    }

    /**
     * fills the trigger time scroll view with integers 1-10
     * @param v: the scrollview
     */
    private void fillScrollView(final View v){
        final int MAX_TRIGGER=10;
        final int MIN_TRIGGER=1;
        for(int i=MIN_TRIGGER; i<=MAX_TRIGGER; i++) {
            TextView view = new TextView(this);
            view.setText(" "+i+" ");
            view.setTextSize(30);
            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    manualTriggerTime = Integer.parseInt(((TextView)view).getText().toString().substring(1,2));
                }
            });
            ((TableRow) v).addView(view);
        }

    }

    /**
     * set the spinners dropdown to include USB/UDP for selecting Telemetry Interface
     * @param v: the spinner
     */
    private void setDroneSpinner(final View v, final int array){

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this,
                array,android.R.layout.simple_spinner_item
        );

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        ((Spinner)v).setAdapter(adapter);
        ((Spinner)v).setOnItemSelectedListener(new SpinnerActivity());

    }

    private void setImageSpinner(final View v, final int array){

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this,
                array,android.R.layout.simple_spinner_item
        );

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        ((Spinner)v).setAdapter(adapter);
        ((Spinner)v).setOnItemSelectedListener(new ImageSizeSpinnerActivity());

    }





    /**
     * callback listener for selecting type on spinner
     */
    private class SpinnerActivity extends Activity implements AdapterView.OnItemSelectedListener {

        public void onItemSelected(AdapterView<?> parent, View view,
                                   int pos, long id) {
            // An item was selected. You can retrieve the selected item using


                droneTelemetry.setConnectionType((CharSequence)parent.getItemAtPosition(pos));

        }

        public void onNothingSelected(AdapterView<?> parent) {
            // Another interface callback
        }
    }

    private class ImageSizeSpinnerActivity extends Activity implements AdapterView.OnItemSelectedListener {

        public void onItemSelected(AdapterView<?> parent, View view,
                                   int pos, long id) {
            // An item was selected. You can retrieve the selected item using
               size = (String) parent.getItemAtPosition(pos);

        }

        public void onNothingSelected(AdapterView<?> parent) {
            // Another interface callback
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        //we don't need to update UI with accell values when phone is asleep
        accelUpdate = true;


        final EditText ipText = (EditText)findViewById(R.id.URL);
        //make keyboard disappear at enter
        hideKeyBoard(ipText);
        hideKeyBoard(findViewById(R.id.username));
        hideKeyBoard(findViewById(R.id.password));
        //fill the scrollview
        fillScrollView(findViewById(R.id.numericintervals));
        //fill the spinner
        setDroneSpinner(findViewById(R.id.connectionType),R.array.drone_connection_types);
        setImageSpinner(findViewById(R.id.imageformat),R.array.image_format_type);

        //callback for clicking telemetry(MAVlink connect button
        findViewById(R.id.droneconnect).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //if connected, disconect
                if(droneTelemetry.status()){
                    droneTelemetry.disconnect();

                }
                //if not connected, connect
                else{
                    droneTelemetry.connect();
                }
            }
        });

        //callback for clicking qx device search
        findViewById(R.id.button_searchqx).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                   searchQx();
            }
        });

        //callback for manual trigger
        findViewById(R.id.button_triggerqx).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                synchronized (cameraTriggerThread) {
                    //testing for qx connection doesn't work here for some reason...
                    //it works when connected remotely, which is really all that matters


                    //if service handler is not null and trigger handler is not triggering
                    if ( !cameraTriggerThread.status()) {
                        //start trigger
                        cameraTriggerThread.setTriggerTime((double) manualTriggerTime);
                        try {
                            cameraTriggerThread.startCapture(false);
                            //UI change
                            ((Button) v).setText(R.string.stopcapture);

                        }
                        catch (IOException e){
                            Log.e(TAG,e.toString());

                        }

                    }
                    //stop triggering
                    else if (cameraTriggerThread.status()) {
                        cameraTriggerThread.stopCapture();
                        //UI change
                        ((Button) v).setText(R.string.startcapture);

                    }
                }


            }
        });

        //start communications to GCS
        findViewById(R.id.remote).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //grab IP address
                EditText ed = (EditText) findViewById(R.id.URL);
                server = ed.getText().toString();
                //use default if empty
                if(server.equals("")){
                    server = "192.168.2.1:2000";
                    ed.setText(server,TextView.BufferType.EDITABLE);
                    alertUser("Using Default IP:PORT");
                }

                //get username/password
                String username = ((EditText)findViewById(R.id.username)).getText().toString();
                String password = ((EditText)findViewById(R.id.password)).getText().toString();

                //if empty
                if(username.equals("") || password.equals("")){
                    alertUser("Bad login");
                    return;
                }


                //start gcs handler
                if(!groundStationHThread.status()){
                    groundStationHThread.setServer(server);
                    groundStationHThread.setTimeout(SERVER_TIMEOUT);
                    //login
                    groundStationHThread.connect(username, password);


                }
                        //sanity check       if connected
                else if(groundStationHThread.status()){
                    //disconnect from GCS
                    groundStationHThread.disconnect();
                    ((Button)view).setText(R.string.gcsconnect);
                }


            }
        });






    }

    @Override
    public void onPause(){
        super.onPause();

        //Nothing...phone might have went to sleep..
        //phone is sleeping, stop UI telem update
        accelUpdate = false;
    }

    @Override
    public void onStop() {
        super.onStop();
        //Nothing...phone might have went to sleep...

    }

    //app stopped
    @Override
    public void onDestroy(){
        //don't forget to clean up :)

        //clean up drone telem with sanity checks
        if(droneTelemetry.status()){
            droneTelemetry.disconnect();
        }

        //clean up camera Trigger thread
        if(cameraTriggerThread.status()){
            cameraTriggerThread.stopCapture();
        }

        //close service connection
        qxCommunicationClient.close();


        //stop GCS thread
        if(groundStationHThread.status()){
            groundStationHThread.disconnect();
        }

        //stop trigger thread
        if(cameraTriggerThread.status()){
            cameraTriggerThread.stopCapture();
        }

        Log.i(TAG,"Destroyed");
        super.onDestroy();


    }




    //used for aleting user of messages
    public void alertUser(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }



}
