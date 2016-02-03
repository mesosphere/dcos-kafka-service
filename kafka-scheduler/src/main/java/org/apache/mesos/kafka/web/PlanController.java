package org.apache.mesos.kafka.web;

import java.util.ArrayList;
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

import org.apache.mesos.kafka.plan.KafkaPlanManager;
import org.apache.mesos.kafka.plan.Phase;
import org.apache.mesos.kafka.scheduler.KafkaScheduler;

import org.apache.mesos.scheduler.plan.Block;

import org.json.JSONArray;
import org.json.JSONObject;

@Path("/v1/plan")
@Produces("application/json")
public class PlanController {
  private final Log log = LogFactory.getLog(PlanController.class);
  private KafkaPlanManager planManager = KafkaScheduler.getPlanManager();

  @GET
  public Response listPhases() {
    try {
      List<Phase> phases = planManager.getPhases();
      JSONObject obj = new JSONObject();
      obj.put("phases", phasesToJsonArray(phases));
      return Response.ok(obj.toString(), MediaType.APPLICATION_JSON).build();
    } catch (Exception ex) {
      log.error("Failed to fetch phases with exception: " + ex);
      return Response.serverError().build();
    }
  }

  @GET
  @Path("/{phaseId}")
  public Response listBlocks(@PathParam("phaseId") String phaseId) {
    try {
      Phase phase = getPhase(Integer.parseInt(phaseId));
      JSONObject obj = new JSONObject();
      obj.put("blocks", blocksToJsonArray(phase.getBlocks()));
      return Response.ok(obj.toString(), MediaType.APPLICATION_JSON).build();
    } catch (Exception ex) {
      log.error("Failed to fetch blocks for phase: " + phaseId + " with exception: " + ex);
      return Response.serverError().build();
    }
  }

  private JSONArray phasesToJsonArray(List<Phase> phases) {
    List<JSONObject> phaseObjs = new ArrayList<JSONObject>();

    for (Phase phase : phases) {
      JSONObject obj = new JSONObject();
      obj.put(Integer.toString(phase.getId()), phase.toString());
      phaseObjs.add(obj);
    }

    return new JSONArray(phaseObjs);
  }

  private JSONArray blocksToJsonArray(List<Block> blocks) {
    List<JSONObject> blockObjs = new ArrayList<JSONObject>();

    for (Block block : blocks) {
      JSONObject descObj = new JSONObject();
      descObj.put("name", block.toString());
      descObj.put("status", block.getStatus().name());

      JSONObject blockObj = new JSONObject();
      blockObj.put(Integer.toString(block.getId()), descObj);

      blockObjs.add(blockObj);
    }

    return new JSONArray(blockObjs);
  }

  private Phase getPhase(int id) {
    List<Phase> phases = planManager.getPhases();

    for (Phase phase : phases) {
      if (phase.getId() == id) {
        return phase;
      }
    }

    return null;
  }
}
