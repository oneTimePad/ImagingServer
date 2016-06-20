package com.ruautonomous.dronecamera;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

/**
 * Created by lie on 6/14/16.
 */
public class CameraTriggerHThread extends HandlerThread {
    /*
    Thread for taking pictures in fixed interval
     */
        private Handler mHandler = null;
        double triggerTime =0.0;
        public QXHandler qxHandler= null;
        public boolean trigger = false;
        public long DEFAULT_TRIGGER_DELAY = 500;
        public String TAG = "CameraTriggerHThread";


        CameraTriggerHThread(){
            super("CameraTriggerHThread");
            start();
            mHandler = new Handler(getLooper());
            this.qxHandler = DroneActivity.app.getQxHandler();
        }

        //setter for telling thread what time to take pictures at
        void setTriggerTime(double time){
            triggerTime = time;
        }

        void stopCapture(){
            trigger = false;
            DroneActivity.app.getContext().alertUser("Capture Stopped");
        }

        boolean status(){
            return trigger;
        }

        //tell thread to start capturing
        void startCapture(){
            if(trigger)return;
            if(triggerTime <= 0.0){
                DroneActivity.app.getContext().alertUser("Bad Trigger Time");
                return;
            }
            DroneActivity.app.getContext().alertUser("Capture Start at "+triggerTime);

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
                            qxHandler.capture();
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
