package com.mesosphere.dcos.kafka.web;

import com.mesosphere.dcos.kafka.commons.state.KafkaState;
import com.mesosphere.dcos.kafka.config.KafkaConfigState;
import com.mesosphere.dcos.kafka.config.KafkaSchedulerConfiguration;
import com.mesosphere.dcos.kafka.config.ServiceConfiguration;
import com.mesosphere.dcos.kafka.state.ClusterState;
import org.apache.mesos.config.ConfigStoreException;
import org.apache.mesos.dcos.Capabilities;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

public class ConnectionControllerTest {

    private static final String FRAMEWORK_NAME = "fwk_name";
    private static final String ZOOKEEPER_ENDPOINT = "test_zk_endpt";

    private static final String BROKER_ENDPOINT_1 = "broker_endpt_1";
    private static final String BROKER_ENDPOINT_2 = "broker_endpt_2";
    private static final List<String> BROKER_ENDPOINTS =
            Arrays.asList(BROKER_ENDPOINT_1, BROKER_ENDPOINT_2);

    private static final String BROKER_DNS_ENDPOINT_1 = "broker_dns_endpt_1";
    private static final String BROKER_DNS_ENDPOINT_2 = "broker_dns_endpt_2";
    private static final List<String> BROKER_DNS_ENDPOINTS =
            Arrays.asList(BROKER_DNS_ENDPOINT_1, BROKER_DNS_ENDPOINT_2);

    @Mock private KafkaConfigState mockKafkaConfigState;
    @Mock private KafkaState mockKafkaState;
    @Mock private KafkaSchedulerConfiguration mockKafkaSchedulerConfiguration;
    @Mock private ServiceConfiguration mockServiceConfiguration;
    @Mock private ClusterState clusterState;
    @Mock private Capabilities capabilities;

    private ConnectionController controller;

    @Before
    public void beforeAll() throws IOException, URISyntaxException {
        MockitoAnnotations.initMocks(this);
        when(clusterState.getCapabilities()).thenReturn(capabilities);
        when(capabilities.supportsNamedVips()).thenReturn(true);
        controller = new ConnectionController(
                ZOOKEEPER_ENDPOINT,
                mockKafkaConfigState,
                mockKafkaState,
                clusterState,
                FRAMEWORK_NAME);
    }

    @Test
    public void testGetConnectionInfo() throws Exception {
        when(mockKafkaState.getBrokerEndpoints()).thenReturn(BROKER_ENDPOINTS);
        mockFrameworkNameRetrieval(FRAMEWORK_NAME);
        when(mockKafkaState.getBrokerDNSEndpoints()).thenReturn(BROKER_DNS_ENDPOINTS);

        Response response = controller.getConnectionInfo();
        assertEquals(200, response.getStatus());

        JSONObject json = new JSONObject((String) response.getEntity());
        assertEquals(4, json.length());
        assertEquals(ZOOKEEPER_ENDPOINT, json.get(ConnectionController.ZOOKEEPER_KEY));

        JSONArray jsonAddress = json.getJSONArray(ConnectionController.ADDRESS_KEY);
        assertEquals(2, jsonAddress.length());
        assertEquals(BROKER_ENDPOINT_1, jsonAddress.get(0));
        assertEquals(BROKER_ENDPOINT_2, jsonAddress.get(1));

        JSONArray jsonDns = json.getJSONArray(ConnectionController.DNS_KEY);
        assertEquals(2, jsonDns.length());
        assertEquals(BROKER_DNS_ENDPOINT_1, jsonDns.get(0));
        assertEquals(BROKER_DNS_ENDPOINT_2, jsonDns.get(1));

        String vip = (String) json.get(ConnectionController.VIP_KEY);
        assertEquals(String.format("broker.%s.l4lb.thisdcos.directory:9092", FRAMEWORK_NAME), vip);
    }

    @Test
    public void testGetConnectionInfoBrokerListFails() throws Exception {
        when(mockKafkaState.getBrokerEndpoints()).thenThrow(new IllegalArgumentException("hi"));
        Response response = controller.getConnectionInfo();
        assertEquals(500, response.getStatus());
    }

    @Test
    public void testGetConnectionInfoTargetConfigFails() throws Exception {
        when(mockKafkaState.getBrokerEndpoints()).thenReturn(BROKER_ENDPOINTS);
        when(mockKafkaConfigState.getTargetConfig()).thenThrow(new ConfigStoreException("hello"));
        Response response = controller.getConnectionInfo();
        assertEquals(500, response.getStatus());
    }

    @Test
    public void testGetConnectionInfoDnsListFails() throws Exception {
        when(mockKafkaState.getBrokerEndpoints()).thenReturn(BROKER_ENDPOINTS);
        mockFrameworkNameRetrieval(FRAMEWORK_NAME);
        when(mockKafkaState.getBrokerDNSEndpoints()).thenThrow(
                new IllegalArgumentException("hi"));
        Response response = controller.getConnectionInfo();
        assertEquals(500, response.getStatus());
    }

    @Test
    public void testGetConnectionAddressInfo() throws Exception {
        when(mockKafkaState.getBrokerEndpoints()).thenReturn(BROKER_ENDPOINTS);

        Response response = controller.getConnectionAddressInfo();
        assertEquals(200, response.getStatus());

        JSONObject json = new JSONObject((String) response.getEntity());
        assertEquals(1, json.length());

        JSONArray jsonAddress = json.getJSONArray(ConnectionController.ADDRESS_KEY);
        assertEquals(2, jsonAddress.length());
        assertEquals(BROKER_ENDPOINT_1, jsonAddress.get(0));
        assertEquals(BROKER_ENDPOINT_2, jsonAddress.get(1));
    }

    @Test
    public void testGetConnectionAddressInfoFails() throws Exception {
        when(mockKafkaState.getBrokerEndpoints()).thenThrow(new IllegalArgumentException("hi"));
        Response response = controller.getConnectionAddressInfo();
        assertEquals(500, response.getStatus());
    }

    @Test
    public void testGetConnectionDNSInfo() throws Exception {
        mockFrameworkNameRetrieval(FRAMEWORK_NAME);
        when(mockKafkaState.getBrokerDNSEndpoints()).thenReturn(BROKER_DNS_ENDPOINTS);

        Response response = controller.getConnectionDNSInfo();
        assertEquals(200, response.getStatus());

        JSONObject json = new JSONObject((String) response.getEntity());
        assertEquals(1, json.length());

        JSONArray jsonDns = json.getJSONArray(ConnectionController.DNS_KEY);
        assertEquals(2, jsonDns.length());
        assertEquals(BROKER_DNS_ENDPOINT_1, jsonDns.get(0));
        assertEquals(BROKER_DNS_ENDPOINT_2, jsonDns.get(1));
    }

    @Test
    public void testGetConnectionDNSInfoTargetConfigFails() throws Exception {
        when(mockKafkaConfigState.getTargetConfig()).thenThrow(new ConfigStoreException("hello"));
        Response response = controller.getConnectionDNSInfo();
        assertEquals(500, response.getStatus());
    }

    @Test
    public void testGetConnectionDNSInfoEndpointFails() throws Exception {
        mockFrameworkNameRetrieval(FRAMEWORK_NAME);
        when(mockKafkaState.getBrokerDNSEndpoints()).thenThrow(
                new IllegalArgumentException("hi"));
        Response response = controller.getConnectionDNSInfo();
        assertEquals(500, response.getStatus());
    }

    private void mockFrameworkNameRetrieval(String name) throws ConfigStoreException {
        when(mockKafkaConfigState.getTargetConfig()).thenReturn(mockKafkaSchedulerConfiguration);
        when(mockKafkaSchedulerConfiguration.getServiceConfiguration())
            .thenReturn(mockServiceConfiguration);
        when(mockServiceConfiguration.getName()).thenReturn(name);
    }
}
