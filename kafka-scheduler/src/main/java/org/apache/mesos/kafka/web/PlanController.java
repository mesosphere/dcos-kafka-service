package org.apache.mesos.kafka.web;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.PUT;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.MediaType;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.mesos.kafka.scheduler.KafkaScheduler;

import org.apache.mesos.scheduler.plan.Block;
import org.apache.mesos.scheduler.plan.PlanManager;
import org.apache.mesos.scheduler.plan.Phase;

import org.json.JSONArray;
import org.json.JSONObject;

import org.apache.mesos.scheduler.plan.Plan;

@Path("/v1/plan")
@Produces("application/json")
public class PlanController {
  private final Log log = LogFactory.getLog(PlanController.class);
  private PlanManager planManager = KafkaScheduler.getPlanManager();

  @GET
  @Path("/status")
  public Response getPlanStatus() {
    try {
      Plan plan = planManager.getPlan();
      Phase phase = plan.getCurrentPhase();
      Block block = null;

      log.info("Building plan obj");
      JSONObject planObj = new JSONObject();
      if (plan != null) {
        planObj.put("phase_count", plan.getPhases().size());
        planObj.put("status", plan.getStatus());
      }

      log.info("Building phase obj");
      JSONObject phaseObj = new JSONObject();
      if (phase != null) {
        block = phase.getCurrentBlock();
        phaseObj.put("name", phase.getName());
        phaseObj.put("index", phase.getId());
        phaseObj.put("block_count", phase.getBlocks().size());
        phaseObj.put("status", phase.getStatus());
      }

      log.info("Building block obj");
      JSONObject blockObj = new JSONObject();
      if (block != null) {
        blockObj.put("name", block.getName());
        blockObj.put("index", block.getId());
        blockObj.put("status", block.getStatus());
      }

      log.info("Building status obj");
      JSONObject statusObj = new JSONObject();
      statusObj.put("plan", planObj);
      statusObj.put("phase", phaseObj);
      statusObj.put("block", blockObj);

      return Response.ok(statusObj.toString(), MediaType.APPLICATION_JSON).build();
    } catch (Exception ex) {
      log.error("Failed to fetch plan status with exception: " + ex);
      return Response.serverError().build();
    }
  }

  @GET
  @Path("/summary")
  public Response getPlanSummary() {
    JSONObject summaryObj = new JSONObject();
    JSONObject planObj = new JSONObject();

    Plan plan = planManager.getPlan();

    if (plan != null) {
      planObj.put("status", plan.getStatus());

      List<JSONObject> phaseObjs = new ArrayList<JSONObject>();
      for (Phase phase : plan.getPhases()) {
        JSONObject phaseObj = new JSONObject();
        phaseObj.put("name", phase.getName());
        phaseObj.put("status", phase.getStatus());

        List<JSONObject> blockObjs = new ArrayList<JSONObject>();
        for (Block block : phase.getBlocks()) {
          JSONObject blockObj = new JSONObject();
          blockObj.put("name", block.getName());
          blockObj.put("status", block.getStatus());
          blockObjs.add(blockObj);
        }

        phaseObj.put("blocks", new JSONArray(blockObjs));
        phaseObjs.add(phaseObj);
      }

      planObj.put("phases", new JSONArray(phaseObjs));
    }

    summaryObj.put("plan", planObj);

    return Response.ok(summaryObj.toString(), MediaType.APPLICATION_JSON).build();
  }

  @GET
  @Path("/phases")
  public Response listPhases() {
    try {
      Plan plan = planManager.getPlan();
      List<? extends Phase> phases = plan.getPhases();
      JSONObject obj = new JSONObject();
      obj.put("phases", phasesToJsonArray(phases));
      return Response.ok(obj.toString(), MediaType.APPLICATION_JSON).build();
    } catch (Exception ex) {
      log.error("Failed to fetch phases with exception: " + ex);
      return Response.serverError().build();
    }
  }

  @GET
  @Path("/phases/{phaseId}")
  public Response listBlocks(@PathParam("phaseId") String phaseId) {
    try {
      Phase phase = getPhase(Integer.parseInt(phaseId));
      JSONObject obj = new JSONObject();
      List<? extends Block> blocks = phase.getBlocks();
      if (blocks != null) {
        obj.put("blocks", blocksToJsonArray(blocks));
        return Response.ok(obj.toString(), MediaType.APPLICATION_JSON).build();
      } else {
        return Response.serverError().build();
      }
    } catch (Exception ex) {
      log.error("Failed to fetch blocks for phase: " + phaseId + " with exception: " + ex);
      return Response.serverError().build();
    }
  }

  @PUT
  @Path("/phases/{phaseId}/{blockId}")
  public Response listBlocks(
      @PathParam("phaseId") String phaseId,
      @PathParam("blockId") String blockId,
      @QueryParam("cmd") String cmd,
      @DefaultValue("false") @QueryParam("force") boolean force) {

    try {
      JSONObject obj = new JSONObject();

      int phaseIndex = Integer.parseInt(phaseId);
      int blockIndex = Integer.parseInt(blockId);

      switch(cmd) {
        case "restart":
          planManager.restart(phaseIndex, blockIndex, force);
          obj.put("Result", "Received cmd: '" + cmd + "' with force set to: '" + force + "'");
          break;
        default:
          log.error("Unrecognized cmd: " + cmd);
          return Response.serverError().build();
      }

      return Response.ok(obj.toString(), MediaType.APPLICATION_JSON).build();

    } catch (Exception ex) {
      log.error("Failed to handle block command with exception: " + ex);
      return Response.serverError().build();
    }
  }

  @PUT
  public Response executeCmd(@QueryParam("cmd") String cmd) {
    try {
      JSONObject obj = new JSONObject();

      switch(cmd) {
        case "continue":
          planManager.proceed();
          obj.put("Result", "Received cmd: " + cmd);
          break;
        case "interrupt":
          planManager.interrupt();
          obj.put("Result", "Received cmd: " + cmd);
          break;
        default:
          log.error("Unrecognized cmd: " + cmd);
          return Response.serverError().build();
      }

      return Response.ok(obj.toString(), MediaType.APPLICATION_JSON).build();

    } catch (Exception ex) {
      log.error("Failed to execute cmd: " + cmd + "  with exception: " + ex);
      return Response.serverError().build();
    }
  }

  private JSONArray phasesToJsonArray(List<? extends Phase> phases) {
    List<JSONObject> phaseObjs = new ArrayList<JSONObject>();

    for (Phase phase : phases) {
      JSONObject obj = new JSONObject();
      obj.put(Integer.toString(phase.getId()), phase.getName());
      phaseObjs.add(obj);
    }

    return new JSONArray(phaseObjs);
  }

  private JSONArray blocksToJsonArray(List<? extends Block> blocks) {
    List<JSONObject> blockObjs = new ArrayList<JSONObject>();

    for (Block block : blocks) {
      JSONObject descObj = new JSONObject();
      descObj.put("name", block.getName());
      descObj.put("status", block.getStatus().name());

      JSONObject blockObj = new JSONObject();
      blockObj.put(Integer.toString(block.getId()), descObj);

      blockObjs.add(blockObj);
    }

    return new JSONArray(blockObjs);
  }

  private Phase getPhase(int id) {
    Plan plan = planManager.getPlan();
    List<? extends Phase> phases = plan.getPhases();

    for (Phase phase : phases) {
      if (phase.getId() == id) {
        return phase;
      }
    }

    return null;
  }
}
