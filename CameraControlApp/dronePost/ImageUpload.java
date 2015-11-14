import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;


public class ImageUpload {

	public static void main(String[] args) throws IOException {

		
		String url_string = "http://127.0.0.1:8000/upload/";
		String file_path_string = "sample_pic3.jpg";
		
		
		
		URL url = new URL(url_string);
		HttpURLConnection con = (HttpURLConnection) url.openConnection();
		
		String boundary_for_multipart_post = "===" + System.currentTimeMillis() + "===";
		con.setRequestProperty("Content-Type",
                "multipart/form-data; boundary=" + boundary_for_multipart_post);
		con.setDoOutput(true);
		OutputStream out = con.getOutputStream();
		out.write( ("--" + boundary_for_multipart_post + "\r\n").getBytes() );
		out.write(( "Content-Disposition: form-data; name=\"pic\"; filename=\"sample_pic4.jpg\"\r\n").getBytes() );
		out.write(( "Content-Type: " + URLConnection.guessContentTypeFromName(new File(file_path_string).getName()) + "\r\n").getBytes() );
		out.write(( "Content-Transfer-Encoding: binary\r\n").getBytes() );
        out.write( ("\r\n").getBytes() );
        out.flush();
        FileInputStream input_stream = new FileInputStream(file_path_string);
        byte[] buffer = new byte[4096];
        int bytes_read = 0;
        while ((bytes_read = input_stream.read(buffer)) > 0) {
            out.write(buffer, 0, bytes_read);
        }
        out.flush();
        input_stream.close();
        out.write( ("\r\n").getBytes() );
        out.write(( "\r\n").getBytes() );
        out.write(( "--" + boundary_for_multipart_post + "--\r\n").getBytes() );
        out.flush();
        out.close();
        
        
        System.out.println(con.getResponseCode());
        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
		String line;
		//look through the html body line by line until you find the line containing the csrftoken
		while ((line=in.readLine()) != null) {
			System.out.println(line);
		}
		
		con.disconnect();
	}

}
