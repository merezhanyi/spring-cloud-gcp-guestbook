package com.example.frontend;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.gcp.pubsub.core.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttributes;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.cloud.gcp.core.GcpProjectIdProvider;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.core.io.WritableResource;
import org.springframework.util.StreamUtils;
import java.io.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.*;

import com.google.cloud.vision.v1.*;
import java.util.ArrayList;
import java.util.List;

@RefreshScope
@Controller
@SessionAttributes("name")
public class FrontendController {
	@Autowired
	private GuestbookMessagesClient client;

	//@Autowired
    //private PubSubTemplate pubSubTemplate;
    @Autowired
	private OutboundGateway outboundGateway;
	
	@Value("${greeting:Hello}")
	private String greeting;

    // We need the ApplicationContext in order to create a new Resource.
	@Autowired
	private ApplicationContext context;

	// We need to know the Project ID, because it's Cloud Storage bucket name
	@Autowired
	private GcpProjectIdProvider projectIdProvider;

    @Autowired
	private ImageAnnotatorClient annotatorClient;
	
	@GetMapping("/")
	public String index(Model model) {
		if (model.containsAttribute("name")) {
			String name = (String) model.asMap().get("name");
			model.addAttribute("greeting", String.format("%s %s", greeting, name));
		}
		model.addAttribute("messages", client.getMessages().getContent());
		return "index";
	}
	
	// Capture the file in request parameter
    @PostMapping("/post")
	public String post(
    @RequestParam(name="file", required=false) MultipartFile file,
    @RequestParam String name, 
    @RequestParam String message, Model model) 
    throws IOException
    {            
		model.addAttribute("name", name);

        String filename = null;
		if (file != null && !file.isEmpty()
			&& file.getContentType().equals("image/jpeg")) {

			// Bucket ID is our Project ID
			String bucket = "gs://" + projectIdProvider.getProjectId();

			// Generate a random file name
			filename = UUID.randomUUID().toString() + ".jpg";
			WritableResource resource = (WritableResource) 
				context.getResource(bucket + "/" + filename);

			// Write the file to Cloud Storage using WritableResource
			try (OutputStream os = resource.getOutputStream()) {
				os.write(file.getBytes());
			}
            
            // After written to GCS, analyze the image.
			analyzeImage(bucket + "/" + filename);

		}
           
		if (message != null && !message.trim().isEmpty()) {
			// Post the message to the backend service
			Map<String, String> payload = new HashMap<>();
			payload.put("name", name);
			payload.put("message", message);

            // Store the generated file name in the database  
			payload.put("imageUri", filename);

			client.add(payload);

			//pubSubTemplate.publish("messages", name + ": " + message);
            outboundGateway.publishMessage(name + ": " + message);
		}
		return "redirect:/";
  }

    // ".+" is necessary to capture URI with filename extension
    @GetMapping("/image/{filename:.+}")
    public ResponseEntity<Resource> file(@PathVariable String filename) {
	    String bucket = "gs://" + projectIdProvider.getProjectId();
        // Use "gs://" URI to construct a Spring Resource object
	    Resource image = context.getResource(bucket + "/" + filename);

	    // Send it back to the client
	    HttpHeaders headers = new HttpHeaders();
	    headers.setContentType(MediaType.IMAGE_JPEG);
	    return new ResponseEntity<>(image, headers, HttpStatus.OK);
    }

    private void analyzeImage(String uri) {
		// After the image was written to GCS, analyze it with the GCS URI.
		// Note: It's also possible to analyze an image embedded in the
		// request as a Base64 encoded payload.
		List<AnnotateImageRequest> requests = new ArrayList<>();
		ImageSource imgSrc = ImageSource.newBuilder()
			.setGcsImageUri(uri).build();
		Image img = Image.newBuilder().setSource(imgSrc).build();
		Feature feature = Feature.newBuilder()
			.setType(Feature.Type.LABEL_DETECTION).build();
		AnnotateImageRequest request = AnnotateImageRequest
			.newBuilder()
			.addFeatures(feature)
			.setImage(img)
			.build();

		requests.add(request);
		BatchAnnotateImagesResponse responses = 
			annotatorClient.batchAnnotateImages(requests);
		// We send in one image, expecting just one response in batch
		AnnotateImageResponse response = responses.getResponses(0);

		System.out.println(response);
	}

}
