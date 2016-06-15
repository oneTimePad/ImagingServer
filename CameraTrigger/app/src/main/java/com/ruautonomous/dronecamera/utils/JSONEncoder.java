package com.ruautonomous.dronecamera.utils;




import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Use to encode input responses from http as json objects
 */
public class JSONEncoder {
    JSONObject response;

    public JSONEncoder(InputStream iStr) throws JSONException,IOException{

        BufferedReader r = new BufferedReader(new InputStreamReader(iStr));
        StringBuilder result = new StringBuilder();
        String line;
        //got through response and make long string
        while ((line = r.readLine()) != null) {
            result.append(line);
        }



        try{
            //create json object from string
            response = new JSONObject(result.toString());

        }
        catch (JSONException e){
            e.printStackTrace();
        }


    }
    //getter for response
    public JSONObject encodeJSON(){
        return response;
    }


}
