package org.apache.mesos.kafka.web;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mesos.Protos.TaskID;
import org.apache.mesos.kafka.scheduler.KafkaScheduler;
import org.apache.mesos.kafka.state.FrameworkStateService;
import org.apache.mesos.kafka.state.KafkaStateService;
import org.apache.mesos.offer.TaskUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Path("/v1/brokers")
@Produces("application/json")
public class BrokerController {
  private final Log log = LogFactory.getLog(BrokerController.class);

  private final KafkaStateService kafkaState;
  private final FrameworkStateService frameworkState;

  public BrokerController(KafkaStateService kafkaState, FrameworkStateService frameworkState) {
    this.kafkaState = kafkaState;
    this.frameworkState = frameworkState;
  }

  @GET
  public Response listBrokers() {
    try {
      JSONArray brokerIds = kafkaState.getBrokerIds();
      JSONObject obj = new JSONObject();
      obj.put("brokers", brokerIds);
      return Response.ok(obj.toString(), MediaType.APPLICATION_JSON).build();
    } catch (Exception ex) {
      log.error("Failed to fetch broker ids", ex);
      return Response.serverError().build();
    }
  }

  @PUT
  @Path("/{id}")
  public Response killBrokers(
    @PathParam("id") String id,
    @QueryParam("replace") String replace) {

    try {
      List<TaskID> taskIds = Arrays.asList(frameworkState.getTaskIdForBroker(Integer.parseInt(id)));
      return killBrokers(taskIds, replace);
    } catch (Exception ex) {
      log.error("Failed to kill brokers", ex);
      return Response.serverError().build();
    }
  }

  private Response killBrokers(List<TaskID> taskIds, String replace) {
    try {
      boolean replaced = Boolean.parseBoolean(replace);

      if (replaced) {
        KafkaScheduler.rescheduleTasks(taskIds);
      } else {
        KafkaScheduler.restartTasks(taskIds);
      }

      List<String> taskNames = new ArrayList<>();
      for (TaskID taskId : taskIds) {
          taskNames.add(TaskUtils.toTaskName(taskId));
      }
      return Response.ok(new JSONArray(taskNames).toString(), MediaType.APPLICATION_JSON).build();
    } catch (Exception ex) {
      log.error("Failed to kill brokers", ex);
      return Response.serverError().build();
    }
  }
}
