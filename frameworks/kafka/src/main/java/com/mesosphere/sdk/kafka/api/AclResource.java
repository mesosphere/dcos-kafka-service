package com.mesosphere.sdk.kafka.api;

import com.mesosphere.sdk.http.ResponseUtils;
import com.mesosphere.sdk.kafka.cmd.CmdExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.ws.rs.*;
import javax.ws.rs.core.Response;

import java.util.Arrays;
import java.util.List;

/**
 * ACL Resource executing commands through command executor.
 * Kafka package should also be deployed with framework to enable Kafka commands.
 */

@Path("/v1/acl")
public class AclResource {
    private static final Logger log = LoggerFactory.getLogger(AclResource.class);

    private final KafkaZKClient kafkaZkClient;
    private final CmdExecutor cmdExecutor;

    public AclResource(CmdExecutor cmdExecutor, KafkaZKClient kafkaZkClient) {
        this.kafkaZkClient = kafkaZkClient;
        this.cmdExecutor = cmdExecutor;
    }

    @GET
    @Path("/list")
    public Response list(
        @QueryParam("cluster") String cluster,
        @QueryParam("topicName") String topicName,
        @QueryParam("consumerGroupName") String consumerGroupName,
        @QueryParam("resourcePatternType") String resourcePatternType,
        @QueryParam("group") String group
    ) {
        String resource = "";
        if(consumerGroupName!= null && !consumerGroupName.isEmpty()) resource = "consumer-group";
        if(topicName!= null && !topicName.isEmpty()) resource = "topic";
        if(cluster != null && !cluster.isEmpty()) resource = "cluster";
        try {
            return ResponseUtils.jsonOkResponse(
                cmdExecutor.configureAcl(
                    "list", resource, topicName, consumerGroupName, resourcePatternType,
                    null, null, null, null, null,
                    false, false, group, null, false
                ));
        } catch (Exception ex) {
            log.error("Failed to fetch acl list with exception: " + ex);
            return Response.serverError().build();
        }
    }

    @PUT
    public Response add(
        @QueryParam("cluster") String cluster,
        @QueryParam("topicName") String topicName,
        @QueryParam("consumerGroupName") String consumerGroupName,
        @QueryParam("resourcePatternType") String resourcePatternType,
        @QueryParam("principal") String principal,
        @QueryParam("principalAction") String principalAction,
        @QueryParam("host") String host,
        @QueryParam("hostAction") String hostAction,
        @QueryParam("operation") String operation,
        @QueryParam("producer") String producerFlag,
        @QueryParam("consumer") String consumerFlag,
        @QueryParam("group") String group,
        @QueryParam("transactionalId") String transactionalId,
        @QueryParam("idempotent") String idempotent
    ) {
        String resource = "";
        Boolean producer = false;
        Boolean consumer = false;
        Boolean _idempotent = false;
        
        if(consumerGroupName!= null && !consumerGroupName.isEmpty()) resource = "consumer-group";
        if(topicName!= null && !topicName.isEmpty()) resource = "topic";
        if(cluster != null && !cluster.isEmpty()) resource = "cluster";

        if(producerFlag != null && !producerFlag.isEmpty()) producer = true;
        if(consumerFlag != null && !consumerFlag.isEmpty()) consumer = true;
        
        if(idempotent != null && !idempotent.isEmpty()) _idempotent = true;

        try {
            return ResponseUtils.jsonOkResponse(
                cmdExecutor.configureAcl(
                    "add", resource, topicName, consumerGroupName, resourcePatternType,
                    principal, principalAction, operation, host, hostAction,
                    producer, consumer, group, transactionalId, _idempotent
                ));
        } catch (Exception ex) {
            log.error("Failed to add acl with exception: " + ex);
            return Response.serverError().build();
        }
    }

    @DELETE
    public Response remove(
        @QueryParam("cluster") String cluster,
        @QueryParam("topicName") String topicName,
        @QueryParam("consumerGroupName") String consumerGroupName,
        @QueryParam("resourcePatternType") String resourcePatternType,
        @QueryParam("principal") String principal,
        @QueryParam("principalAction") String principalAction,
        @QueryParam("host") String host,
        @QueryParam("hostAction") String hostAction,
        @QueryParam("operation") String operation,
        @QueryParam("producer") String producerFlag,
        @QueryParam("consumer") String consumerFlag,
        @QueryParam("group") String group,
        @QueryParam("transactionalId") String transactionalId,
        @QueryParam("idempotent") String idempotent
    ) {
        String resource = "";
        Boolean producer = false;
        Boolean consumer = false;
        Boolean _idempotent = false;
        
        if(consumerGroupName!= null && !consumerGroupName.isEmpty()) resource = "consumer-group";
        if(topicName!= null && !topicName.isEmpty()) resource = "topic";
        if(cluster != null && !cluster.isEmpty()) resource = "cluster";

        if(producerFlag != null && !producerFlag.isEmpty()) producer = true;
        if(consumerFlag != null && !consumerFlag.isEmpty()) consumer = true;

        if(idempotent != null && !idempotent.isEmpty()) _idempotent = true;
        
        try {
            return ResponseUtils.jsonOkResponse(
                cmdExecutor.configureAcl(
                    "remove", resource, topicName, consumerGroupName, resourcePatternType,
                    principal, principalAction, operation, host, hostAction,
                    producer, consumer, group, transactionalId, _idempotent
                ));
        } catch (Exception ex) {
            log.error("Failed to remove acl with exception: " + ex);
            return Response.serverError().build();
        }
    }
}
