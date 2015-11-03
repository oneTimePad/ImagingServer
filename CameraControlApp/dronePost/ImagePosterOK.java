import FormEncoding.*;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import java.io.*;

public class ImagePosterOK {

	public static final MediaType MEDIA_TYPE_IMAGE = MediaType.parse("image/png");

	public static final OkHttpClient client = new OkHttpClient();

	public static void main(String[] args) throws IOException {
		File file = new File("Gatsbae.png");

		FormEncoding input = new FormEncoding.Builder()
		.add("text","test")
		.add("image",file)
		.build();

		Request request = new Request.Builder()
			.url("192.168.205.118:90")
			.post(input)
			.build();

		//Request request = new Request.Builder()
		//	.url("192.168.205.118:90")
		//	.post(RequestBody.create(MEDIA_TYPE_IMAGE, file))
		//	.build();

		Response response = client.newCall(request).execute();
		if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

		System.out.println(response.body().string());
	}
}
