package com.mesosphere.dcos.kafka.state;

import org.apache.mesos.dcos.Capabilities;
import org.apache.mesos.dcos.DcosCluster;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.net.URISyntaxException;

/**
 * This class exposes information about the DC/OS cluster in which it is running.
 */
public class ClusterState {
    private final Log log = LogFactory.getLog(getClass());
    private DcosCluster dcosCluster;
    private Capabilities capabilities;

    public ClusterState() throws URISyntaxException {
        this(new DcosCluster());
    }

    public ClusterState(DcosCluster dcosCluster) {
        this.dcosCluster = dcosCluster;
        this.capabilities = new Capabilities(dcosCluster);
    }

    public Capabilities getCapabilities() {
        return capabilities;
    }
}
