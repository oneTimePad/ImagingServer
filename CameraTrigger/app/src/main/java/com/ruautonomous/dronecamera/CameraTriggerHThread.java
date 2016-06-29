package com.ruautonomous.dronecamera;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.ruautonomous.dronecamera.qxservices.QXCommunicationClient;
import com.ruautonomous.dronecamera.qxservices.QXHandler;

import java.io.IOException;

/**
 * Thread handler for triggering Qx
 */
public class CameraTriggerHThread extends HandlerThread {
    /*
    Thread for taking pictures in fixed interval
     */
        private Handler mHandler = null;
        private QXCommunicationClient qxCommunicationClient;
        double triggerTime =0.0;

        public boolean trigger = false;
        public long DEFAULT_TRIGGER_DELAY = 500;
        public String TAG = "CameraTriggerHThread";
        public DroneActivity context;



        public CameraTriggerHThread(DroneActivity context){
            super("CameraTriggerHThread");
            start();
            this.context = context;
            mHandler = new Handler(getLooper());
            ;

        }

    /**
     * sets important instance variables based on other objects
     */
    public void set(){
            this.qxCommunicationClient = DroneSingleton.qxCommunicationClient;
        }

        //setter for telling thread what time to take pictures at
        void setTriggerTime(double time){
            triggerTime = time;
        }

        void stopCapture(){
            trigger = false;

        }





        boolean status(){
            return trigger;
        }

        //tell thread to start capturing
        void startCapture(boolean check) throws IOException{
            if(trigger)return;
            if(triggerTime <= 0.0){
                context.alertUser("Bad Trigger Time");
                throw new IOException("bad trigger time");
            }

            if(check) {

                if (!qxCommunicationClient.qxStatus()) return;
            }
            context.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    context.alertUser("Capture Started: "+triggerTime+"fps");
                }
            });



            mHandler.post(new Runnable() {
                @Override
                public void run() {

                    trigger = true;

                    //capture looop
                    while (trigger) {

                        //time at start of loop
                        long timeBeforeCapture = System.currentTimeMillis();

                        //wait given time interval
                        try {
                            //add service
                            qxCommunicationClient.trigger();
                            Log.i(TAG,"Capture");
                            //time at end of loop
                            long timeAfterCapture = System.currentTimeMillis();
                            //necessary delay
                            double timeDelta = (triggerTime*1000 - (timeAfterCapture - timeBeforeCapture));

                            //wait the delay
                            if(timeDelta>=0) {
                                Thread.sleep((long) (timeDelta));
                            }
                            else{
                                //almost never gets executed...to stop crashes
                                Thread.sleep(DEFAULT_TRIGGER_DELAY);
                            }




                        } catch (InterruptedException e) {
                            Log.e(TAG,e.toString());
                        }
                    }
                }
            });
        }

}
