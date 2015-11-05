import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/* 
 What this file is doing: 
 	1) submit an http get request to a url containing a django form
 	2) extract the csrf token from the http response
 	3) submit an http post request to a url containing a post action,
 		passing in the csrf token twice: once in a cookie and once as
 		as a post parameter
 */


public class JustPostRequest {

	public static void main(String[] args) throws IOException {
		
		//url for post form
		URL url = new URL("http://127.0.0.1:8000/humans/post/");
		HttpURLConnection con = (HttpURLConnection) url.openConnection();
		con.setRequestMethod("GET");
		
		/*
		Note: at this point, no connection to the url has actually been established yet.
			Connections can be established explicitly using con.connect() or implicitly
			using something like con.getResponseCode(), which calls con.connect() if the connection
			has not been established yet
		*/
		
		System.out.println("Get request to " + url);
		//establish the connection and print the response code (hopefully 200)
		System.out.println("    " + con.getResponseCode());
		
		//get first cookie from header of httpreponse.  For my django implementation, the csrf token cookie is the only cookie, which means it must be the first one
		String csrf_token_full_cookie = con.getHeaderField("Set-Cookie");
		//extract the actual csrf token from the cookie by substringing the cookie
		String csrf_token_value_from_cookie = csrf_token_full_cookie.substring(csrf_token_full_cookie.indexOf("=") + 1, csrf_token_full_cookie.indexOf(";"));
		System.out.println("    csrf token extracted from cookie: " + csrf_token_value_from_cookie);
		
		/*
		 Note: httpresponse sends the csrf token twice: once in a cookie and once in the html body.
		 	I already extracted the csrf token from the cookie, so I don't need to extract it again from
		 	the html body.  However, I am doing it anyway, just to prove that the two csrf tokens are the
		 	same.
		 */
		
		String csrf_token_value_from_html_body = "";
		//created buffered reader wrapped around httpresponse's html body
		BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
		String line;
		//look through the html body line by line until you find the line containing the csrftoken
		while ((line=in.readLine()) != null) {
			int index_of_csrfmiddlewaretoken = line.indexOf("csrfmiddlewaretoken");
			if (index_of_csrfmiddlewaretoken != -1) {
				csrf_token_value_from_html_body = line.substring(line.indexOf("value=")+7, line.indexOf("/>")-2);
				break;
			}
		}
		//print the csrf token extracted from html body, just to prove that its the same token that was extracted from the cookie
		System.out.println("    csrf token extracted from html body: " + csrf_token_value_from_html_body);
		
		
		System.out.println();
		
		//url for post action
		URL url2 = new URL("http://127.0.0.1:8000/humans/create_new_human/");
		HttpURLConnection con2 = (HttpURLConnection) url2.openConnection();
		//need to call following line for post requests
		con2.setDoOutput(true);
		con2.setRequestMethod("POST");
		
		//add the csrf token to the http request as a cookie
		con2.setRequestProperty("Cookie", "csrftoken=" + csrf_token_value_from_cookie);
		
		//create a string containing all the post data, including a csrftoken entry.  all the values except for the csrftoken value are arbitrary
		String post_data = "name=bran&age=3&description=never gonna GIVE YOU UP, never gonna LET YOU DOWN&csrfmiddlewaretoken=" + csrf_token_value_from_cookie;
		//convert post data string to bytes
		byte[] post_data_binary = post_data.getBytes();
		//add the post data to the http post request
		DataOutputStream wr2 = new DataOutputStream(con2.getOutputStream());
		wr2.write(post_data_binary);
		wr2.flush();
		wr2.close();
		
		System.out.println("Post request to " + url2);
		
		//establish the connection and print the response code (hopefully 200)
		System.out.println("    " + con2.getResponseCode());	
	}

}
