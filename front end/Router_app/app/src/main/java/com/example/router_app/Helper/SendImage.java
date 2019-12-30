package com.example.router_app.Helper;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.lang.Object.*;
import java.util.regex.Pattern;


public class SendImage extends AsyncTask<Void, String, String> {
    public AsyncResponse delegate = null;

    private ProgressDialog progress;
    public String realPath;
    public String UPLOAD_SERVER = "https://homedatabase-060e.restdb.io/media?&apikey=5dc5641764e7774913b6ea76";
    public Context context;
    private String uploadResponse = "";
    public Boolean asyncTackCompleted = false;

    @Override
    protected void onPreExecute() {
        progress = new ProgressDialog(context);
        progress.setTitle("Uploading image....");
        progress.setMessage("Please wait until the process is finished");
        progress.setCancelable(false); // disable dismiss by tapping outside of the dialog
        progress.show();
    }

    @Override
    protected String doInBackground(Void... params) {

        String response;
        try {
            response = POST_Data(realPath);
        } catch (Exception e) {
            //Log.d("myTest", e.toString());
            response = e.toString();
        }
        return response;
    }

    @Override
    protected void onPostExecute(String result) {
        progress.dismiss();
        //Log.d("myTest",result);
        if (result.contains("uploadid")) {
            String stringId = result.substring(result.indexOf("[") + 1, result.indexOf("]"));
            String stringId2 = stringId.replace("\"", "");

            // Log.d("myTest","test string is: "+stringId2);
            uploadResponse = stringId2;
            delegate.processFinish(uploadResponse);
        } else {
            delegate.processFinish("wrong");
        }

    }

    private String POST_Data(String filepath) throws Exception {
        HttpURLConnection connection = null;
        DataOutputStream outputStream = null;
        InputStream inputStream = null;
        String boundary = "*****" + Long.toString(System.currentTimeMillis()) + "*****";
        int bytesRead, bytesAvailable, bufferSize;
        byte[] buffer;
        String[] q = filepath.split("/");
        int idx = q.length - 1;
        File file = new File(filepath);
        FileInputStream fileInputStream = new FileInputStream(file);
        URL url = new URL(UPLOAD_SERVER);
        connection = (HttpURLConnection) url.openConnection();
        connection.setDoInput(true);
        connection.setDoOutput(true);
        connection.setUseCaches(false);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Connection", "Keep-Alive");
        connection.setRequestProperty("User-Agent", "Android Multipart HTTP Client 1.0");
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        outputStream = new DataOutputStream(connection.getOutputStream());
        outputStream.writeBytes("--" + boundary + "\r\n");
        outputStream.writeBytes("Content-Disposition: form-data; name=\"" + "img_upload" + "\"; filename=\"" + q[idx] + "\"" + "\r\n");
        outputStream.writeBytes("Content-Type: image/jpeg" + "\r\n");
        outputStream.writeBytes("Content-Transfer-Encoding: binary" + "\r\n");
        outputStream.writeBytes("\r\n");
        bytesAvailable = fileInputStream.available();
        bufferSize = Math.min(bytesAvailable, 1048576);
        buffer = new byte[bufferSize];
        bytesRead = fileInputStream.read(buffer, 0, bufferSize);
        while (bytesRead > 0) {
            outputStream.write(buffer, 0, bufferSize);
            bytesAvailable = fileInputStream.available();
            bufferSize = Math.min(bytesAvailable, 1048576);
            bytesRead = fileInputStream.read(buffer, 0, bufferSize);
        }
        outputStream.writeBytes("\r\n");
        outputStream.writeBytes("--" + boundary + "--" + "\r\n");
        inputStream = connection.getInputStream();
        //JSONObject obj = new JSONObject(connection.getResponseMessage());
        int status = connection.getResponseCode();
        if (status == HttpURLConnection.HTTP_OK || status == 201) {
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            inputStream.close();
            connection.disconnect();
            fileInputStream.close();
            outputStream.flush();
            outputStream.close();
            return response.toString();
        } else {
            //Log.d("myTest", "status " + status);
            throw new Exception("Non ok response returned");
        }
    }
}

