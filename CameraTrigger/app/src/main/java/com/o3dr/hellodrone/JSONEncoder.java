package com.o3dr.hellodrone;




import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Created by lie on 12/30/15.
 */
public class JSONEncoder {
    JSONObject response;

    public JSONEncoder(InputStream iStr) throws JSONException,IOException{

        BufferedReader r = new BufferedReader(new InputStreamReader(iStr));
        StringBuilder result = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) {
            result.append(line);
        }



        try{
            response = new JSONObject(result.toString());

        }
        catch (JSONException e){
            e.printStackTrace();
        }


    }

    public JSONObject encodeJSON(){
        return response;
    }


}
