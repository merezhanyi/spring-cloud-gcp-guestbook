package com.example.frontend;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.Resources;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Map;

// We can use a number of different clients. For the lab, we'll use Feign.
// For simplicity, we'll just use Map to represent the entities.
// We'll default the endpoint to localhost for now, this will be overridden.
@FeignClient(
	value="messages", 
	url="${messages.endpoint:http://localhost:8081/guestbookMessages}")
public interface GuestbookMessagesClient {
	@RequestMapping(method=RequestMethod.GET, path="/")
	Resources<Map> getMessages();
	
	@RequestMapping(method=RequestMethod.GET, path="/{id}")
	Map getMessage(@PathVariable("id") long messageId);
	
	@RequestMapping(method=RequestMethod.POST, path="/")
	Resource<Map> add(@RequestBody Map message);
}

