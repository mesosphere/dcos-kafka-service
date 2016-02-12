package org.apache.mesos.kafka.web;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import javax.ws.rs.GET;

import javax.ws.rs.Path;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.mesos.config.ConfigurationService;
import org.apache.mesos.kafka.config.KafkaConfigService;
import org.apache.mesos.kafka.state.KafkaStateService;

import org.json.JSONArray;
import org.json.JSONObject;


@Path("/v1")
public class ClusterController {
  private final Log log = LogFactory.getLog(ClusterController.class);
  private KafkaStateService state = KafkaStateService.getStateService();
  private KafkaConfigService config =  KafkaConfigService.getEnvConfig();

  private String zkRoot = "/" + config.get("FRAMEWORK_NAME");
  private String zkAddr = config.getZookeeperAddress();

  @Path("/connection")
  @GET
  public Response getConnectionInfo() {
    try {
      JSONObject connectionInfo = new JSONObject();
      String zookeeperEndpoint = zkAddr + "/" + config.get("FRAMEWORK_NAME");
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
