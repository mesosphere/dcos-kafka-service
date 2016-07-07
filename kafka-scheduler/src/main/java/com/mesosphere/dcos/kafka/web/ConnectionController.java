package com.mesosphere.dcos.kafka.web;

import com.mesosphere.dcos.kafka.config.KafkaConfigState;
import com.mesosphere.dcos.kafka.state.KafkaState;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;


@Path("/v1")
public class ConnectionController {
    private static final Log log = LogFactory.getLog(ConnectionController.class);
    static final String ZOOKEEPER_KEY = "zookeeper";
    static final String ADDRESS_KEY = "address";
    static final String DNS_KEY = "dns";

    private final String zookeeperEndpoint;
    private final KafkaState state;
    private final KafkaConfigState configState;

    public ConnectionController(
            String zookeeperEndpoint, KafkaConfigState configState, KafkaState state) {
        this.zookeeperEndpoint = zookeeperEndpoint;
        this.configState = configState;
        this.state = state;
    }

    @Path("/connection")
    @GET
    public Response getConnectionInfo() {
        try {
            JSONObject connectionInfo = new JSONObject();
            connectionInfo.put(ZOOKEEPER_KEY, zookeeperEndpoint);
            connectionInfo.put(ADDRESS_KEY, getBrokerList());
            connectionInfo.put(DNS_KEY, getBrokerDNSList());
            return Response.ok(connectionInfo.toString(), MediaType.APPLICATION_JSON).build();
        } catch (Exception ex) {
            log.error("Failed to fetch topics with exception: " + ex);
            return Response.serverError().build();
        }
    }

    @Path("/connection/address")
    @GET
    public Response getConnectionAddressInfo() {
        try {
            JSONObject connectionInfo = new JSONObject();
            connectionInfo.put(ADDRESS_KEY, getBrokerList());
            return Response.ok(connectionInfo.toString(), MediaType.APPLICATION_JSON).build();
        } catch (Exception ex) {
            log.error("Failed to fetch topics with exception: " + ex);
            return Response.serverError().build();
        }
    }

    @Path("/connection/dns")
    @GET
    public Response getConnectionDNSInfo() {
        try {
            JSONObject connectionInfo = new JSONObject();
            connectionInfo.put(DNS_KEY, getBrokerDNSList());
            return Response.ok(connectionInfo.toString(), MediaType.APPLICATION_JSON).build();
        } catch (Exception ex) {
            log.error("Failed to fetch topics with exception: " + ex);
            return Response.serverError().build();
        }
    }

    private JSONArray getBrokerList() throws Exception {
        return new JSONArray(state.getBrokerEndpoints());
    }

    private JSONArray getBrokerDNSList() throws Exception {
        final String frameworkName =
                configState.getTargetConfig().getServiceConfiguration().getName();
        return new JSONArray(state.getBrokerDNSEndpoints(frameworkName));
    }
}
