package com.o3dr.dronecamera.utils;

/**
 * Created by lie on 6/8/16.
 */
public class BooleanObject{

    private boolean on;

    public BooleanObject(boolean def){
        this.on = def;

    }

    public void set(boolean val){
        this.on = val;
    }
    public boolean get(){
        return this.on;
    }
}
