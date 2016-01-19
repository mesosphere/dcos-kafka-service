package org.apache.mesos.kafka.state;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.RetryPolicy;
import org.apache.curator.retry.ExponentialBackoffRetry;

import org.apache.mesos.config.ConfigurationService;
import org.apache.mesos.kafka.config.KafkaConfigService;

import org.apache.mesos.Protos.FrameworkID;
import org.apache.mesos.Protos.TaskInfo;

import java.util.List;

public class KafkaStateService {
  private final Log log = LogFactory.getLog(KafkaStateService.class);
  private final CuratorFramework zkClient;
  
  private static ConfigurationService config = KafkaConfigService.getConfigService();

  private static final Integer POLL_DELAY_MS = 1000;
  private static final Integer CURATOR_MAX_RETRIES = 3;
  private static String zkRoot;
  private static String taskPath;
  private static String fwkIdPath;

  private static KafkaStateService stateService = null;

  public static KafkaStateService getStateService() {
    if (stateService == null) {
      stateService = new KafkaStateService();
    }

    return stateService;
  }

  public FrameworkID getFrameworkId() {
    FrameworkID fwkId = null;
    try {
      byte[] bytes = zkClient.getData().forPath(fwkIdPath);
      if (bytes.length > 0) {
        return FrameworkID.parseFrom(bytes);
      }
    } catch(Exception ex) {
      log.error("Failed to get FrameworkID with exception: " + ex);
    }

    return null;
  }

  public void setFrameworkId(FrameworkID fwkId) {
    try {
      zkClient.setData().forPath(fwkIdPath, fwkId.toByteArray());
    } catch(Exception ex) {
      log.error("Failed to set FrameworkID: " + fwkId + " with exception: " + ex);
    }
  }

  public void recordTasks(List<TaskInfo> taskInfos) throws Exception {
    for (TaskInfo taskInfo : taskInfos) {
      String infoPath = taskPath + "/" + taskInfo.getName();
      zkClient.create().forPath(infoPath, taskInfo.toByteArray());
    }
  }

  private KafkaStateService() {
    zkClient = createCuratorClient();

    zkRoot = "/" + config.get("FRAMEWORK_NAME") + "/state";
    taskPath = zkRoot + "/tasks";
    fwkIdPath = zkRoot + "/framework-id";

    try {
      initializePath(zkRoot + "/tasks");
      initializePath(zkRoot + "/framework-id");
    } catch(Exception ex) {
      log.fatal("Failed with exception: " + ex);
    }
  }

  private void initializePath(String path) throws Exception {
    if (zkClient.checkExists().forPath(path) == null) {
      zkClient.create().creatingParentsIfNeeded().forPath(path, new byte[0]);
    }
  }

  private CuratorFramework createCuratorClient() {
    String hosts = config.get("ZOOKEEPER_ADDR");
    RetryPolicy retryPolicy = new ExponentialBackoffRetry(POLL_DELAY_MS, CURATOR_MAX_RETRIES);

    CuratorFramework client = CuratorFrameworkFactory.newClient(hosts, retryPolicy);
    client.start();

    return client;
  }
} 
