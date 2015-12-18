package com.o3dr.hellodrone;

/* What this class's sendPostRequest() method does:
 *      - Conducts an http post request
 *      - Data sent using the http post request:
 *      	- File Upload: An image file
 *      	- Post Data: A csrf token (sends a csrf token at the bare minimum, if you want to send more than that, you have to tweak my code)
 *      	- Cookie: A cookie containing the same csrf token
 */


import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map.Entry;


public class ImageUpload {

    //url that you are posting to
    private String url_string;
    //path to the directory on the phone where the images are saved
    private String directory_containing_images_string;
    //map containing keys and values of any other post data that you want to send
    //in the post request. My code as it is now initializes this map to be empty,
    //so if your server requires additional key-value pairs to be sent, you have to
    //add them yourself



    public ImageUpload(String url_string) throws IllegalAccessException {
        if(url_string==null){
            throw new IllegalAccessError("No URL") ;
        }

        this.url_string = url_string+"/upload";

    }


    public void sendPostRequest(JSONObject request_data) throws IOException {

        URL url = new URL(this.url_string);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");

        String csrf_token_string = "sss";
        //sets the csrf token cookie
        con.setRequestProperty("Cookie", "csrftoken=" + csrf_token_string);
        con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        //set doOutput to true so that we can write bytes to the body of the http post request
        con.setDoOutput(true);
        //initialize the output stream that will write to the body of the http post request
        OutputStream out = con.getOutputStream();

        OutputStreamWriter outW = new OutputStreamWriter(out);
        outW.write(request_data.toString());
        outW.flush();
        outW.close();

        switch(con.getResponseCode()){
            case 200:
                Log.d("200","Success");
                break;
            case 500:
                Log.e("500", "Internal Server error");
                break;
            case 403:
                Log.e("403","Forbidden");
                break;
            default:
                Log.e("#","Something else");
        }
    }



}
