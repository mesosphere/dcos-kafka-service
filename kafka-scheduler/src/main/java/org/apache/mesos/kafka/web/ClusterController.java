package org.apache.mesos.kafka.web;

import java.util.List;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import javax.ws.rs.GET;

import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.mesos.kafka.config.KafkaConfigState;
import org.apache.mesos.kafka.state.KafkaStateService;

import org.json.JSONArray;
import org.json.JSONObject;


@Path("/v1")
public class ClusterController {
  private final Log log = LogFactory.getLog(ClusterController.class);
  private final String zookeeperEndpoint;
  private final KafkaStateService state;
  private final KafkaConfigState configState;

  public ClusterController(
      String zookeeperEndpoint,
      KafkaConfigState configState,
      KafkaStateService state) {
    this.zookeeperEndpoint = zookeeperEndpoint;
    this.configState = configState;
    this.state = state;
  }

  @Path("/connection")
  @GET
  public Response getConnectionInfo() {
    try {
      JSONObject connectionInfo = new JSONObject();
      connectionInfo.put("zookeeper", zookeeperEndpoint);
      connectionInfo.put("brokers", getBrokerList());
      connectionInfo.put("zookeeper_convenience", getConvenientZookeeper(zookeeperEndpoint));
      connectionInfo.put("broker_list_convenience", getConvenientBrokerList());

      return Response.ok(connectionInfo.toString(), MediaType.APPLICATION_JSON).build();

    } catch (Exception ex) {
      log.error("Failed to fetch topics with exception: " + ex);
      return Response.serverError().build();
    }
  }

  @Path("/configurations")
  @GET
  public Response getConfigurations() {
    try {
      List<String> configNames = configState.getConfigNames();
      JSONArray configArray = new JSONArray(configNames);
      return Response.ok(configArray.toString(), MediaType.APPLICATION_JSON).build();

    } catch (Exception ex) {
      log.error("Failed to fetch configurations with exception: " + ex);
      return Response.serverError().build();
    }
  }

  @Path("/configurations/{configurationName}")
  @GET
  public Response getConfigurations(@PathParam("configurationName") String configurationName) {
    try {
      log.info("Attempting to fetch config: " + configurationName);

      for (String configName : configState.getConfigNames()) {
        if (configName.equals(configurationName)) {
          JSONObject configObj = new JSONObject(configState.fetch(configName));
          return Response.ok(configObj.toString(), MediaType.APPLICATION_JSON).build();
        } else {
          log.warn(configName + " doesn't equal " + configurationName);
        }
      }

      log.error("Failed to find configuration: " + configurationName);
      return Response.serverError().build();

    } catch (Exception ex) {
      log.error("Failed to fetch configurations with exception: " + ex);
      return Response.serverError().build();
    }
  }

  @Path("/configurations/target")
  @GET
  public Response getTargetConfiguration() {
    try {
      log.info("Attempting to fetch config: " + configState.getTargetName());

      JSONObject configObj = new JSONObject(configState.getTargetConfig());
      return Response.ok(configObj.toString(), MediaType.APPLICATION_JSON).build();

    } catch (Exception ex) {
      log.error("Failed to fetch target configuration with exception: " + ex);
      return Response.serverError().build();
    }
  }

  private JSONArray getBrokerList() throws Exception {
    return new JSONArray(state.getBrokerEndpoints());
  }

  private String getConvenientZookeeper(String zookeeperEndpoint) {
    return "--zookeeper " + zookeeperEndpoint;
  }

  private String getConvenientBrokerList() {
    String brokerList = "--broker-list ";

    try {
      String brokers = String.join(", ", state.getBrokerEndpoints());
      return brokerList + brokers;
    } catch (Exception ex) {
      log.error("Failed to fetch broker endpoints for convenience with exception : " + ex);
    }

    return brokerList;
  }
}
