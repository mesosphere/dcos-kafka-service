package com.mesosphere.dcos.kafka.web;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mesos.scheduler.plan.*;

import org.json.JSONObject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.stream.Collectors;

@Path("/v1")
@Produces("application/json")
public class InterruptProceed {
    private static final Log log = LogFactory.getLog(ConnectionController.class);
    private PlanManager planManager;

    public InterruptProceed(PlanManager planManager) {
        this.planManager = planManager;
    }

    @POST
    @Path("/continue")
    public Response continueCommand() {
        try {
            List<Phase> phases = planManager.getPlan().getChildren().stream()
                    .filter(p -> p.getName().toString().equals("Deployment"))
                    .collect(Collectors.toList());
            phases.forEach(p -> p.getStrategy().proceed());
            return Response.ok(new JSONObject().put("Result","Received cmd: continue").toString(),
                    MediaType.APPLICATION_JSON)
                    .build();
        } catch (Exception e) {
            log.error("Failed to process interrupt request: " + e);
            return Response.serverError().build();
        }
    }

    @POST
    @Path("/interrupt")
    public Response interruptCommand() {
        try {
            List<Phase> phases = planManager.getPlan().getChildren().stream()
                    .filter(p -> p.getName().toString().equals("Deployment"))
                    .collect(Collectors.toList());
            phases.forEach(p -> p.getStrategy().interrupt());
            return Response.ok(new JSONObject().put("Result","Received cmd: interrupt").toString(),
                     MediaType.APPLICATION_JSON)
                    .build();
        } catch (Exception e) {
            log.error("Failed to process continue request: " + e);
            return Response.serverError().build();
        }
    }
}
