package org.apache.mesos.kafka.testclient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Namespace for POJO classes containing configuration for various parts of our clients.
 */
public class ClientConfigs {

  private static final Logger LOGGER = LoggerFactory.getLogger(ClientConfigs.class);

  public enum ClientMode {
    NONE,
    PRODUCER,
    CONSUMER,
  }

  /**
   * POJO containing client bootstrap options.
   */
  public static class StartupConfig {
    public static final String FRAMEWORK_NAME = "FRAMEWORK_NAME";

    public final ClientMode clientMode;
    public final String frameworkName;

    /**
     * Returns {@code null} if parsing fails.
     */
    public static StartupConfig parseFrom(Map<String, String> testClientConfig) {
      try {
        String frameworkName = get(testClientConfig, FRAMEWORK_NAME, null);
        ClientMode clientMode = ClientMode.valueOf(get(testClientConfig, "MODE", "NONE"));
        if (clientMode == ClientMode.NONE) {
          throw new Exception("MODE argument is required. "
              + "Must be either " + ClientMode.PRODUCER.toString() + " or " + ClientMode.CONSUMER.toString());
        }
        return new StartupConfig(clientMode, frameworkName);
      } catch (Throwable e) {
        printFlagParseFailure(e);
        return null;
      }
    }

    private StartupConfig(ClientMode clientMode, String frameworkName) {
      this.clientMode = clientMode;
      this.frameworkName = frameworkName;
    }
  }

  /**
   * POJO containing stats emitter options.
   */
  public static class StatsConfig {
    public final long printPeriodMs;

    /**
     * Returns {@code null} if parsing fails.
     */
    public static StatsConfig parseFrom(Map<String, String> testClientConfig) {
      try {
        long printPeriodMs = Long.parseLong(get(testClientConfig, "STATS_PRINT_PERIOD_MS", "500"));
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
   * POJO containing test consumer options.
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
        long pollTimeoutMs = Long.parseLong(get(testClientConfig, "POLL_TIMEOUT_MS", "1000"));
        int threads = Integer.parseInt(get(testClientConfig, "THREADS", "5"));
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
   * POJO containing test producer options.
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
        boolean synchronous = Boolean.parseBoolean(get(testClientConfig, "SYNCHRONOUS", "true"));
        int threads = Integer.parseInt(get(testClientConfig, "THREADS", "5"));
        int qpsLimit = Integer.parseInt(get(testClientConfig, "QPS_LIMIT", "5"));
        String topic = get(testClientConfig, "TOPIC", "bench_topic");
        int messageSize = Integer.parseInt(get(testClientConfig, "MESSAGE_SIZE_BYTES", "1024"));
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
  private static String lastGetKey = "";
  private static String lastGetValue = "";

  private static void printFlagParseFailure(Throwable e) {
    LOGGER.error(String.format("Failed to parse value for arg %s=%s", lastGetKey, lastGetValue), e);
  }

  private static String get(Map<String, String> testClientConfig, String key, String defaultVal) {
    lastGetKey = key;
    String setVal = testClientConfig.get(key);
    String val = (setVal != null) ? setVal : defaultVal;
    lastGetValue = val;
    return val;
  }
}
