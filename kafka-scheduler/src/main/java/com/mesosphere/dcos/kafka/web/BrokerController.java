package com.mesosphere.dcos.kafka.web;

import com.mesosphere.dcos.kafka.scheduler.KafkaScheduler;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mesos.Protos;
import com.mesosphere.dcos.kafka.state.FrameworkState;
import com.mesosphere.dcos.kafka.state.KafkaState;
import org.json.JSONArray;

import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.util.Arrays;
import java.util.List;

@Path("/v1/brokers")
@Produces("application/json")
public class BrokerController {
  private final Log log = LogFactory.getLog(BrokerController.class);

  private final KafkaState kafkaState;
  private final FrameworkState frameworkState;

  public BrokerController(KafkaState kafkaState, FrameworkState frameworkState) {
    this.kafkaState = kafkaState;
    this.frameworkState = frameworkState;
  }

  @GET
  public Response listBrokers() {
    try {
      return Response.ok(kafkaState.getBrokerIds(), MediaType.APPLICATION_JSON).build();
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
      int idVal = Integer.parseInt(id);
      Protos.TaskInfo taskInfo = frameworkState.getTaskInfoForBroker(idVal);
      if (taskInfo == null) {
        // Tests expect an array containing a single null element in this case. May make sense to
        // revisit this strange behavior someday...
        log.error(String.format(
            "Broker %d doesn't exist in FrameworkState, returning null entry in response", idVal));
        return killResponse(Arrays.asList((String)null));
      }
      return killBroker(taskInfo, Boolean.parseBoolean(replace));
    } catch (Exception ex) {
      log.error("Failed to kill brokers", ex);
      return Response.serverError().build();
    }
  }

  private Response killBroker(Protos.TaskInfo taskInfo, boolean replace) {
    try {
      if (replace) {
        KafkaScheduler.rescheduleTask(taskInfo);
      } else {
        KafkaScheduler.restartTasks(taskInfo);
      }
      return killResponse(Arrays.asList(taskInfo.getTaskId().getValue()));
    } catch (Exception ex) {
      log.error("Failed to kill brokers", ex);
      return Response.serverError().build();
    }
  }

  private static Response killResponse(List<String> taskIds) {
    return Response.ok(new JSONArray(taskIds).toString(), MediaType.APPLICATION_JSON).build();
  }
}
