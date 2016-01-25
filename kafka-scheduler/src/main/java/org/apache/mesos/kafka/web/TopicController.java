package org.apache.mesos.kafka.web;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;

import javax.ws.rs.Consumes;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.MediaType;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mesos.kafka.cmd.CmdExecutor;
import org.apache.mesos.kafka.state.KafkaStateService;

import org.json.JSONArray;
import org.json.JSONObject;

@Path("/topics")
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

  @POST
  public Response createTopic(
      @QueryParam("name") String name,
      @QueryParam("partitions") String partitionCount,
      @QueryParam("replication") String replicationFactor) {

    try {
      int partCount = Integer.parseInt(partitionCount);
      int replFactor = Integer.parseInt(replicationFactor);
      JSONObject result = CmdExecutor.createTopic(name, partCount, replFactor);
      return Response.ok(result.toString(), MediaType.APPLICATION_JSON).build();
    } catch (Exception ex) {
      log.error("Failed to create topic: " + name + " with exception: " + ex);
      return Response.serverError().build();
    }
  }

  @GET
  @Path("/{name}")
  public Response getTopic(@PathParam("name") String topicName) {
    try {
      JSONObject topic = state.getTopic(topicName);
      return Response.ok(topic.toString(), MediaType.APPLICATION_JSON).build();
    } catch (Exception ex) {
      log.error("Failed to fetch topic: " + topicName + " with exception: " + ex);
      return Response.serverError().build();
    }
  }

  @PUT
  @Path("/{name}")
  public Response testTopic(
      @PathParam("name") String name,
      @QueryParam("test") String testType,
      @QueryParam("messages") String messages) {

    try {
      JSONObject result = null;

      switch (testType) {
        case "producer":
          int messageCount = Integer.parseInt(messages);
          result = CmdExecutor.producerTest(name, messageCount);
          break;
        default:
          result = new JSONObject();
          result.put("Error", "Unrecognized test type: " + testType);
          break;
      }

      return Response.ok(result.toString(), MediaType.APPLICATION_JSON).build();

    } catch (Exception ex) {
      log.error("Failed to run performance test: " + testType + "on Topic: " + name +  " with exception: " + ex);
      return Response.serverError().build();
    }
  }

}
