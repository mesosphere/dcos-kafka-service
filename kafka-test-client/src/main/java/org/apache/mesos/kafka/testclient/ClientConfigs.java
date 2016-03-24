package org.apache.mesos.kafka.testclient;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Namespace for POJO classes containing configuration for various parts of our clients.
 */
public class ClientConfigs {

  private static final Logger LOGGER = LoggerFactory.getLogger(ClientConfigs.class);

  /**
   * POJO containing stats emitter options
   */
  public static class StatsConfig {
    public final long printPeriodMs;

    /**
     * Returns {@code null} if parsing fails.
     */
    public static StatsConfig parseFrom(Map<String, String> testClientConfig) {
      try {
        long printPeriodMs = Long.valueOf(get(testClientConfig, "STATS_PRINT_PERIOD_MS", "500"));
        return new StatsConfig(printPeriodMs);
      } catch (Throwable e) {
        printFlagParseFailure(e);
        return null;
      }
    }

    private StatsConfig(long printPeriodMs) {
      this.printPeriodMs = printPeriodMs;
    }
  }

  /**
   * POJO containing broker lookup options
   */
  public static class BrokerLookupConfig {
    public final String masterHost;
    public final String frameworkName;

    /**
     * Returns {@code null} if parsing fails.
     */
    public static BrokerLookupConfig parseFrom(Map<String, String> testClientConfig) {
      try {
        String masterHost = get(testClientConfig, "MASTER_HOST", "master.mesos");
        String frameworkName = get(testClientConfig, "FRAMEWORK_NAME", "kafka");
        return new BrokerLookupConfig(masterHost, frameworkName);
      } catch (Throwable e) {
        printFlagParseFailure(e);
        return null;
      }
    }

    private BrokerLookupConfig(String masterHost, String frameworkName) {
      this.masterHost = masterHost;
      this.frameworkName = frameworkName;
    }
  }

  /**
   * POJO containing test consumer options
   */
  public static class ConsumerConfig {
    public final long pollTimeoutMs;
    public final int threads;
    public final String topic;

    /**
     * Returns {@code null} if parsing fails.
     */
    public static ConsumerConfig parseFrom(Map<String, String> testClientConfig) {
      try {
        long pollTimeoutMs = Long.valueOf(get(testClientConfig, "POLL_TIMEOUT_MS", "1000"));
        int threads = Integer.valueOf(get(testClientConfig, "THREADS", "5"));
        String topic = get(testClientConfig, "TOPIC", "bench_topic");
        return new ConsumerConfig(pollTimeoutMs, threads, topic);
      } catch (Throwable e) {
        printFlagParseFailure(e);
        return null;
      }
    }

    private ConsumerConfig(long pollTimeoutMs, int threads, String topic) {
      this.pollTimeoutMs = pollTimeoutMs;
      this.threads = threads;
      this.topic = topic;
    }
  }

  /**
   * POJO containing test producer options
   */
  public static class ProducerConfig {
    public final boolean synchronous;
    public final int threads;
    public final int qpsLimit;
    public final String topic;
    public final int messageSize;

    /**
     * Returns {@code null} if parsing fails.
     */
    public static ProducerConfig parseFrom(Map<String, String> testClientConfig) {
      try {
        boolean synchronous = Boolean.valueOf(get(testClientConfig, "SYNCHRONOUS", "true"));
        int threads = Integer.valueOf(get(testClientConfig, "THREADS", "5"));
        int qpsLimit = Integer.valueOf(get(testClientConfig, "QPS_LIMIT", "5"));
        String topic = get(testClientConfig, "TOPIC", "bench_topic");
        int messageSize = Integer.valueOf(get(testClientConfig, "MESSAGE_SIZE_BYTES", "1024"));
        return new ProducerConfig(synchronous, threads, qpsLimit, topic, messageSize);
      } catch (Throwable e) {
        printFlagParseFailure(e);
        return null;
      }
    }

    private ProducerConfig(boolean synchronous, int threads, int qpsLimit, String topic, int messageSize) {
      this.synchronous = synchronous;
      this.threads = threads;
      this.qpsLimit = qpsLimit;
      this.topic = topic;
      this.messageSize = messageSize;
    }
  }


  /**
   * Local hack to provide a bridge between get() and printFlagParseFailure().
   */
  private static String LAST_GET_KEY = "";
  private static String LAST_GET_VALUE = "";

  private static void printFlagParseFailure(Throwable e) {
    LOGGER.error(String.format("Failed to parse value for arg %s=%s", LAST_GET_KEY, LAST_GET_VALUE), e);
  }

  private static String get(Map<String, String> testClientConfig, String key, String defaultVal) {
    LAST_GET_KEY = key;
    String setVal = testClientConfig.get(key);
    String val = (setVal != null) ? setVal : defaultVal;
    LAST_GET_VALUE = val;
    return val;
  }
}
