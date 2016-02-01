package org.apache.mesos.kafka.state;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;

import org.apache.curator.RetryPolicy;
import org.apache.curator.retry.ExponentialBackoffRetry;

public class KafkaStateUtils {
  private static final Integer POLL_DELAY_MS = 1000;
  private static final Integer CURATOR_MAX_RETRIES = 3;

  public static CuratorFramework createZkClient(String hosts) {
    RetryPolicy retryPolicy = new ExponentialBackoffRetry(POLL_DELAY_MS, CURATOR_MAX_RETRIES);

    CuratorFramework client = CuratorFrameworkFactory.newClient(hosts, retryPolicy);
    client.start();

    return client;
  }
}
