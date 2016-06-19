package com.ruautonomous.dronecamera.utils;



/*
 * Copyright 2014 Sony Corporation
 */

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;


import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;

/**
 * Simple HTTP Client for sample application.
 */
public final class SimpleHttpClient {

    private static final String TAG = SimpleHttpClient.class.getSimpleName();

    private static final int DEFAULT_CONNECTION_TIMEOUT = 10000; // [msec]

    private static final int DEFAULT_READ_TIMEOUT = 10000; // [msec]

    private SimpleHttpClient() {

    }

    /**
     * Send HTTP GET request to the indicated url. Then returns response as
     * string.
     *
     * @param url request target
     * @return response as string
     * @throws IOException all errors and exception are wrapped by this
     *             Exception.
     */
    public static String httpGet(String url) throws IOException {
        return httpGet(url, DEFAULT_READ_TIMEOUT);
    }

    /**
     * Send HTTP GET request to the indicated url. Then returns response as
     * string.
     *
     * @param url request target
     * @param timeout Request timeout
     * @return response as string
     * @throws IOException all errors and exception are wrapped by this
     *             Exception.
     */
    public static String httpGet(String url, int timeout) throws IOException {
        HttpURLConnection httpConn = null;
        InputStream inputStream = null;

        // Open connection and input stream
        try {
            final URL urlObj = new URL(url);
            httpConn = (HttpURLConnection) urlObj.openConnection();
            httpConn.setRequestMethod("GET");
            httpConn.setConnectTimeout(DEFAULT_CONNECTION_TIMEOUT);
            httpConn.setReadTimeout(timeout);
            httpConn.connect();

            int responseCode = httpConn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                inputStream = httpConn.getInputStream();
            }
            if (inputStream == null) {
                Log.w(TAG, "httpGet: Response Code Error: " + responseCode + ": " + url);
                throw new IOException("Response Error:" + responseCode);
            }
        } catch (final SocketTimeoutException e) {
            Log.w(TAG, "httpGet: Timeout: " + url);
            throw new IOException();
        } catch (final MalformedURLException e) {
            Log.w(TAG, "httpGet: MalformedUrlException: " + url);
            throw new IOException();
        } catch (final IOException e) {
            Log.w(TAG, "httpGet: " + e.getMessage());
            if (httpConn != null) {
                httpConn.disconnect();
            }
            throw e;
        }

        // Read stream as String
        BufferedReader reader = null;
        try {
            StringBuilder responseBuf = new StringBuilder();
            reader = new BufferedReader(new InputStreamReader(inputStream));
            int c;
            while ((c = reader.read()) != -1) {
                responseBuf.append((char) c);
            }
            return responseBuf.toString();
        } catch (IOException e) {
            Log.w(TAG, "httpGet: read error: " + e.getMessage());
            throw e;
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
                Log.w(TAG, "IOException while closing BufferedReader");
            }
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (IOException e) {
                Log.w(TAG, "IOException while closing InputStream");
            }
        }
    }


    public static JSONObject httpPostPicture(String url, JSONObject postData, String imageName, byte[] image, HashMap<String,String> authorization)throws IOException{
            return httpPostPicture(url,postData,imageName,image,authorization,DEFAULT_READ_TIMEOUT);



    }


    public static JSONObject httpPostPicture(String url, JSONObject postData,String imageName, byte[] image, HashMap<String,String> authorization, int timeout)throws IOException{
        HttpURLConnection httpConn = null;
        InputStream inputStream = null;
        PrintWriter wrt = null;
        OutputStream outputStream = null;
        OutputStreamWriter outputStreamWriter = null;
        JSONObject response = null;

        //check if refresh is necessary before posting
        //refresh();
        //open connection to server
       try{

        final URL urlObj = new URL(url);
        httpConn = (HttpURLConnection) urlObj.openConnection();
        httpConn.setRequestMethod("POST");
        httpConn.setConnectTimeout(DEFAULT_CONNECTION_TIMEOUT);
        httpConn.setReadTimeout(timeout);
        httpConn.setDoInput(true);
        httpConn.setDoOutput(true);
        if(authorization!=null && authorization.containsKey("accesstoken")) {
            httpConn.setRequestProperty("Authorization", "JWT " + authorization.get("accesstoken"));
        }
        else{
            Log.e(TAG,"authorization required to make this method call");
            throw new IOException("Authentication will failed. Access token required");
        }
        if(imageName==null || image==null || postData ==null){
            Log.e(TAG,"one or more required arguments is null");
            throw  new IOException("Missing arguments");
        }


            //form multipart request
            //dispositions
            String jsonDisp = "Content-Disposition: form-data; name=\"jsonData\"";
            String jsonType = "Content-Type: application/json; charset=UTF-8";

            String fileDisp = "Content-Disposition: form-data; name=\"Picture\"; filename=\"" + imageName + "\"";
            String fileType = "Content-Type: image/jpeg";

            String LINE_FEED = "\r\n";

            //content type
            String boundary = "===" + System.currentTimeMillis() + "===";
            httpConn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            httpConn.setRequestProperty("ENCTYPE", "multipart/form-data");
            //add image name
            httpConn.setRequestProperty("image", imageName);
            //might wanna not keep-alive
            httpConn.setRequestProperty("Connection", "Keep-Aive");
            //open connection
            httpConn.connect();

            outputStream = httpConn.getOutputStream();
            outputStreamWriter = new OutputStreamWriter(outputStream);
            //write image and image data
            wrt = new PrintWriter(outputStreamWriter, true);

            wrt.append("--" + boundary).append(LINE_FEED);
            wrt.append(fileDisp).append(LINE_FEED);
            wrt.append(fileType).append(LINE_FEED);

            wrt.append(LINE_FEED);
            wrt.flush();

            outputStream.write(image);
            outputStream.flush();

            wrt.append(LINE_FEED);
            wrt.flush();

            //write json to request
            wrt.append("--" + boundary).append(LINE_FEED);
            wrt.append(jsonDisp).append(LINE_FEED);
            wrt.append(jsonType).append(LINE_FEED);
            wrt.append(LINE_FEED);
            wrt.append(postData.toString());
            wrt.append(LINE_FEED);
            wrt.flush();

            int responseCode = httpConn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                inputStream = httpConn.getInputStream();
                response = new JSONEncoder(inputStream).encodeJSON();

            }
            if (inputStream == null) {
                Log.w(TAG, "httpPost: Response Code Error: " + responseCode + ": " + url);
                throw new IOException("Response Error:" + responseCode);

            }
            wrt.close();
            outputStream.close();
            outputStreamWriter.close();
        } catch (final JSONException e) {
            Log.w(TAG, "httpPost: JSONException: " + url);
            throw new IOException();
        } catch (final SocketTimeoutException e) {
            Log.w(TAG, "httpPost: Timeout: " + url);
            throw new IOException();
        } catch (final MalformedURLException e) {
            Log.w(TAG, "httpPost: MalformedUrlException: " + url);
            throw new IOException();
        } catch (final IOException e) {
            Log.w(TAG, "httpPost: IOException: " + e.getMessage());
            if (httpConn != null) {
                httpConn.disconnect();
            }
            throw e;
        } finally {

                if (wrt != null) {
                    wrt.close();
                }

            try {
                if (outputStream != null) {
                    outputStream.close();
                }
            } catch (IOException e) {
                Log.w(TAG, "IOException while closing OutputStream");
            }

            try {
                if (outputStreamWriter != null) {
                    outputStreamWriter.close();
                }
            } catch (IOException e) {
                Log.w(TAG, "IOException while closing OutputStream");
            }
        }
        return  response;
    }


    /**
     * Send HTTP POST request to the indicated url. Then returns response as
     * string.
     *
     * @param url request target
     * @param postData POST body data as string (ex. JSON)
     * @return response as string
     * @throws IOException all errors and exception are wrapped by this
     *             Exception.
     */
    public static JSONObject httpPost(String url, JSONObject postData,HashMap<String,String> authorization) throws IOException {
        return httpPost(url, postData, authorization, DEFAULT_READ_TIMEOUT);
    }

    /**
     * Send HTTP POST request to the indicated url. Then returns response as
     * string.
     *
     * @param url request target
     * @param postData POST body data as string (ex. JSON)
     * @param timeout Request timeout
     * @return response as string
     * @throws IOException all errors and exception are wrapped by this
     *             Exception.
     */
    public static JSONObject httpPost(String url, JSONObject postData, HashMap<String,String> authorization, int timeout) throws IOException {
        HttpURLConnection httpConn = null;
        OutputStream outputStream = null;
        OutputStreamWriter writer = null;
        InputStream inputStream = null;
        JSONObject response = null;
        // Open connection and input stream
        try {
            final URL urlObj = new URL(url);
            httpConn = (HttpURLConnection) urlObj.openConnection();
            httpConn.setRequestMethod("POST");
            httpConn.setConnectTimeout(DEFAULT_CONNECTION_TIMEOUT);
            httpConn.setReadTimeout(timeout);
            httpConn.setDoInput(true);
            httpConn.setDoOutput(true);
            httpConn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            if(authorization!=null && authorization.containsKey("accesstoken")) {
                httpConn.setRequestProperty("Authorization", "JWT " + authorization.get("accesstoken"));
            }

            outputStream = httpConn.getOutputStream();
            writer = new OutputStreamWriter(outputStream, "UTF-8");
            writer.write(postData.toString());
            writer.flush();
            writer.close();
            writer = null;
            outputStream.close();
            outputStream = null;

            int responseCode = httpConn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                inputStream = httpConn.getInputStream();
                response = new JSONEncoder(inputStream).encodeJSON();

            }
            if (inputStream == null) {
                Log.w(TAG, "httpPost: Response Code Error: " + responseCode + ": " + url);
                throw new IOException("Response Error:" + responseCode);

            }
        } catch (final JSONException e) {
            Log.w(TAG, "httpPost: JSONException: " + url);
            throw new IOException();
        } catch (final SocketTimeoutException e) {
            Log.w(TAG, "httpPost: Timeout: " + url);
            throw new IOException();
        } catch (final MalformedURLException e) {
            Log.w(TAG, "httpPost: MalformedUrlException: " + url);
            throw new IOException();
        } catch (final IOException e) {
            Log.w(TAG, "httpPost: IOException: " + e.getMessage());
            if (httpConn != null) {
                httpConn.disconnect();
            }
            throw e;
        } finally {
            try {
                if (writer != null) {
                    writer.close();
                }
            } catch (IOException e) {
                Log.w(TAG, "IOException while closing OutputStreamWriter");
            }
            try {
                if (outputStream != null) {
                    outputStream.close();
                }
            } catch (IOException e) {
                Log.w(TAG, "IOException while closing OutputStream");
            }
        }
        return  response;
    }



}

