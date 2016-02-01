package org.apache.mesos.kafka.config;

import java.util.Map;

import org.apache.curator.framework.CuratorFramework;

import org.apache.mesos.config.ConfigProperty;
import org.apache.mesos.config.FrameworkConfigurationService;

import org.apache.mesos.config.state.ConfigState;
import org.apache.mesos.kafka.state.KafkaStateUtils;
import org.apache.mesos.state.StateStoreException;

public class KafkaConfigState {
  private CuratorFramework zkClient = null;
  private ConfigState configState = null;

  public KafkaConfigState(String frameworkName, String hosts, String rootZkPath) {
    zkClient = KafkaStateUtils.createZkClient(hosts);
    configState = new ConfigState(frameworkName, rootZkPath, zkClient);
  }

  public void store(FrameworkConfigurationService configurationService, String version) throws StateStoreException {
    configState.store(configurationService, version);
  }

  public Map<String, Map<String, ConfigProperty>> fetch(String version) throws StateStoreException {
    return configState.fetch(version);
  }
}
