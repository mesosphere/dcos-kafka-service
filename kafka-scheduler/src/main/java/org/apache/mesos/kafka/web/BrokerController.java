package org.apache.mesos.kafka.web;

import java.util.Arrays;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.PUT;

import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.MediaType;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.mesos.kafka.scheduler.KafkaScheduler;
import org.apache.mesos.kafka.state.KafkaStateService;

import org.json.JSONArray;
import org.json.JSONObject;

@Path("/brokers")
@Produces("application/json")
public class BrokerController {
  private final Log log = LogFactory.getLog(BrokerController.class);
  private KafkaStateService state = KafkaStateService.getStateService();

  @GET
  public Response brokers() {
    try {
      JSONArray brokerIds = state.getBrokerIds();
      return Response.ok(brokerIds.toString(), MediaType.APPLICATION_JSON).build();
    } catch (Exception ex) {
      log.error("Failed to fetch broker ids with exception: " + ex);
      return Response.serverError().build();
    }
  }

  @PUT
  public Response killBrokers(@QueryParam("reschedule") String reschedule) {
    try {
      List<String> taskIds = state.getTaskIds();
      return killBrokers(taskIds, reschedule);
    } catch (Exception ex) {
      log.error("Failed to kill brokers with exception: " + ex);
      return Response.serverError().build();
    }
  }

  @GET
  @Path("/{id}")
  public Response broker(@PathParam("id") String id) {
    try {
      JSONObject broker = state.getBroker(id);
      return Response.ok(broker.toString(), MediaType.APPLICATION_JSON).build();
    } catch (Exception ex) {
      log.error("Failed to fetch broker: " + id + " with exception: " + ex);
      return Response.serverError().build();
    }
  }

  @PUT
  @Path("/{id}")
  public Response killBrokers(
      @PathParam("id") String id,
      @QueryParam("reschedule") String reschedule) {

    try {
      List<String> taskIds = 
        Arrays.asList(state.getTaskIdForBroker(Integer.parseInt(id)));
      return killBrokers(taskIds, reschedule);
    } catch (Exception ex) {
      log.error("Failed to kill brokers with exception: " + ex);
      return Response.serverError().build();
    }
  }

  private Response killBrokers(List<String> taskIds, String reschedule) {
    try {
      if (reschedule == null || reschedule == "false") {
        KafkaScheduler.restartTasks(taskIds);
      } else if (reschedule.equals("true")) {
        KafkaScheduler.rescheduleTasks(taskIds);
      } else {
        JSONObject failed = new JSONObject();
        failed.put("Failed",  "'reschedule' = " + reschedule);
        return Response.ok(failed.toString(), MediaType.APPLICATION_JSON).build();
      }

      return Response.ok(new JSONArray(taskIds).toString(), MediaType.APPLICATION_JSON).build();
    } catch (Exception ex) {
      log.error("Failed to kill brokers with exception: " + ex);
      return Response.serverError().build();
    }
  }

}
