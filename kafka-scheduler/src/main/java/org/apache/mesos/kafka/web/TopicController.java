package org.apache.mesos.kafka.web;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.MediaType;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mesos.kafka.state.KafkaStateService;

import org.json.JSONArray;
import org.json.JSONObject;

@Path("/topics")
@Produces("application/json")
public class TopicController {
  private final Log log = LogFactory.getLog(TopicController.class);
  private KafkaStateService state = KafkaStateService.getStateService();

  @GET
  public Response topics() {
    try {
      JSONArray topics = state.getTopics();
      return Response.ok(topics.toString(), MediaType.APPLICATION_JSON).build();
    } catch (Exception ex) {
      log.error("Failed to fetch topics with exception: " + ex);
      return Response.serverError().build();
    }
  }

  @GET
  @Path("/{name}")
  public Response topic(@PathParam("name") String topicName) {
    try {
      JSONObject topic = state.getTopic(topicName);
      return Response.ok(topic.toString(), MediaType.APPLICATION_JSON).build();
    } catch (Exception ex) {
      log.error("Failed to fetch topic: " + topicName + " with exception: " + ex);
      return Response.serverError().build();
    }
  }
}
