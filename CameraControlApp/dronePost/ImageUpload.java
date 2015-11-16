/* What this java file's main method does:
 *      - Conducts an http post request
 *      - Data sent using the http post request:
 *      	- File Upload: An image file
 *      	- Post Data: A csrf token (sends a csrf token at the bare minimum, if you want to send more than that, you have to tweak my code)
 *      	- Cookie: A cookie containing the same csrf token
 */


import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map.Entry;


public class ImageUpload {

	public static void main(String[] args) throws IOException {

		/* Note: If you want to tweak my code so that it works with your server,
		 * 		the only lines that you should tweak are the lines in the next
		 *  	paragraph, where I define the url_string, file_path_string, and
		 *      additional_post_keys_and_values variables. My later code uses these
		 * 		three variables to properly conduct the http post request, so you
		 * 		should only tweak the initialization of these variables.
		 */
		
		//url that will take the post request
		final String url_string = "http://0.0.0.0:70/upload";
		//file path for the image that you want to upload
		final String file_path_string = "sample_pic_from_arrow.jpg";
		//map containing keys and values of any other post data that you want to send
		//in the post request. My code as it is now initializes this map to be empty,
		//so if your server requires additional key-value pairs to be sent, you have to
		//add them yourself
		final HashMap<String, String> additional_post_keys_and_values = new HashMap<String, String>();
		//If you want to add key-value pairs to the map: follow the format of this next commented line.
		additional_post_keys_and_values.put("text", "test");
	
		
		
		URL url = new URL(url_string);
		HttpURLConnection con = (HttpURLConnection) url.openConnection();
		con.setRequestMethod("POST");
		
		
		/* Note: at this point, no connection to the url has actually been established yet.
		 * 		Connections can be established explicitly using con.connect() or implicitly
		 * 		using something like con.getResponseCode(), which calls con.connect() if the connection
		 * 		has not been established yet
		*/
		
		
		//arbitrary csrf token that we will be using.  We need to send this csrf token
		//in the post request twice: once in a cookie and once as a key-value pair in the post request body.
		//If we do not do this correctly, django will send back a 403 forbidden response
		String csrf_token_string = "sss";
		//sets the csrf token cookie
		con.setRequestProperty("Cookie", "csrftoken=" + csrf_token_string);
		
		
		
		/* Note: This http post request includes a file upload. In order to make file
		 * 		uploads work, we have to make it a multipart/form data post request (by setting
		 * 		the header of the http request.  When we make the http post request a 
		 * 		multipart/form data post request, we also have to provide our boundary string.
		 * 		What is a boundary string? Well, we can compare it to "&", the separation string 
		 * 		for simple posts (not multipart/form data) posts.  For example, if you just wanted to post
		 * 		a name and an age (no file uploads), you would send the string "name=Paul&age=23"
		 * 		in the post request body. In that example, the "&" separates between name
		 * 		and age. That is basically what the boundary string does for multipart/form data
		 * 		posting. It allows the server accepting the post request to differentiate between
		 * 		different post entities, including file uploads and simple post data. Comparing the 
		 * 		boundary string to "&" is a good starting point, but there are actually many
		 *      differences between them.  One important difference is that when using &, the post
		 *      data comes immediately after the &, whereas when using a boundary string, the post
		 *      data does not come immediately after the and. Metadata describing the post data comes
		 *      immediately after the boundary string, then comes the data itself.  There are many other
		 *      differences between & and the boundary string, but I am not going to explain them all here.
		 *      Just trust me that I am using the boundary string correctly.
		 */
		
		//The boundary string that we will be using. 
		String boundary_for_multipart_post = "===" + System.currentTimeMillis() + "===";
		//Make the post request a multipart/form data post request, and pass in the boundary string
		con.setRequestProperty("Content-Type",
                "multipart/form-data; boundary=" + boundary_for_multipart_post);
		
		
		
		//set doOutput to true so that we can write bytes to the body of the http post request
		con.setDoOutput(true);
		//initialize the output stream that will write to the body of the http post request
		OutputStream out = con.getOutputStream();
		
		
		//following paragraph writes any additional key-value post data pairs (as defined at the top of the main method) to the body
		for (Entry<String, String> key_value_pair: additional_post_keys_and_values.entrySet()) {
			String key = key_value_pair.getKey();
			String value = key_value_pair.getValue();
			out.write( ("--" + boundary_for_multipart_post + "\r\n").getBytes() );
			out.write( ("Content-Disposition: form-data; name=\"" + key + "\"\r\n").getBytes() );
			out.write( ("\r\n").getBytes() );
			out.write( (value).getBytes() );
			out.flush();
		}
		
		//following paragraph writes the csrf token into the body as a key-value pair
		out.write( ("--" + boundary_for_multipart_post + "\r\n").getBytes() );
		out.write( ("Content-Disposition: form-data; name=\"csrfmiddlewaretoken\"\r\n").getBytes() );
		out.write( ("\r\n").getBytes() );
		out.write( (csrf_token_string).getBytes() );
		out.flush();
		
		
		//following paragraph writes the file upload into the body
		out.write( ("--" + boundary_for_multipart_post + "\r\n").getBytes() );
		out.write( ("Content-Disposition: form-data; name=\"pic\"; filename=\"" + file_path_string + "\"\r\n").getBytes() );
		out.write( ("Content-Type: " + URLConnection.guessContentTypeFromName(new File(file_path_string).getName()) + "\r\n").getBytes() );
		out.write( ("Content-Transfer-Encoding: binary\r\n").getBytes() );
        out.write( ("\r\n").getBytes() );
        out.flush();
        //initialize the file input stream that will read from the file
        FileInputStream input_stream = new FileInputStream(file_path_string);
        byte[] buffer = new byte[4096];
        int bytes_read = 0;
        //following while loop reads through the binary data from the file, passes the binary data into a buffer, then from the buffer writes the binary data to the http post request body
        while ((bytes_read = input_stream.read(buffer)) > 0) {
            out.write(buffer, 0, bytes_read);
        }
        out.flush();
        input_stream.close();
        out.write( ("\r\n").getBytes() );
        out.write( ("\r\n").getBytes() );
        out.write( ("--" + boundary_for_multipart_post + "--\r\n").getBytes() );
        out.flush();
        out.close();
        
        
        
        
        //establish the connection and print the response code (hopefully 200)
        System.out.println(con.getResponseCode());
        //end the connection
		con.disconnect();
	}

}
