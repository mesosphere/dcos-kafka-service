package org.apache.mesos.kafka.cmd;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import javax.ws.rs.core.MultivaluedMap;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.mesos.config.ConfigurationService;
import org.apache.mesos.config.FrameworkConfigurationService;
import org.apache.mesos.kafka.state.KafkaStateService;
import org.apache.mesos.kafka.config.KafkaConfigService;

import org.json.JSONArray;
import org.json.JSONObject;

public class CmdExecutor {
  private static final Log log = LogFactory.getLog(CmdExecutor.class);
  private static KafkaConfigService config = KafkaConfigService.getEnvConfig();
  private static KafkaStateService state = KafkaStateService.getStateService();

  private static String binPath = config.get("MESOS_SANDBOX") + "/" + config.get("KAFKA_VER_NAME") + "/bin/";
  private static String zkPath = config.getKafkaZkUri();

  public static JSONObject createTopic(String name, int partitionCount, int replicationFactor) throws Exception {
    // e.g. ./kafka-topics.sh --create --zookeeper master.mesos:2181/kafka-0 --topic topic0 --partitions 3 --replication-factor 3

    List<String> cmd = new ArrayList<String>();
    cmd.add(binPath + "kafka-topics.sh");
    cmd.add("--create");
    cmd.add("--zookeeper");
    cmd.add(zkPath);
    cmd.add("--topic");
    cmd.add(name);
    cmd.add("--partitions");
    cmd.add(Integer.toString(partitionCount));
    cmd.add("--replication-factor");
    cmd.add(Integer.toString(replicationFactor));

    return runCmd(cmd);
  }

  public static JSONObject deleteTopic(String name) throws Exception {
    // e.g. ./kafka-topics.sh --delete --zookeeper master.mesos:2181/kafka0 --topic topic0

    List<String> cmd = new ArrayList<String>();
    cmd.add(binPath + "kafka-topics.sh");
    cmd.add("--delete");
    cmd.add("--zookeeper");
    cmd.add(zkPath);
    cmd.add("--topic");
    cmd.add(name);

    return runCmd(cmd);
  }

  public static JSONObject alterTopic(String name, List<String> cmds) throws Exception {
    // e.g. ./kafka-topics.sh --zookeeper master.mesos:2181/kafka0 --alter --topic topic0 --partitions 4

    List<String> cmd = new ArrayList<String>();
    cmd.add(binPath + "kafka-topics.sh");
    cmd.add("--alter");
    cmd.add("--zookeeper");
    cmd.add(zkPath);
    cmd.add("--topic");
    cmd.add(name);
    cmd.addAll(cmds);

    return runCmd(cmd);
  }

  public static List<String> getListOverrides(MultivaluedMap<String, String> overrides) {
    List<String> output = new ArrayList<String>();

    for (Map.Entry<String,List<String>> override : overrides.entrySet()) {
      output.add("--" + override.getKey());
      output.add(override.getValue().get(0));
    }

    return output;
  }

  public static JSONObject producerTest(String topicName, int messages) throws Exception {
    // e.g. ./kafka-producer-perf-test.sh --topic topic0 --num-records 1000 --producer-props bootstrap.servers=ip-10-0-2-171.us-west-2.compute.internal:9092,ip-10-0-2-172.us-west-2.compute.internal:9093,ip-10-0-2-173.us-west-2.compute.internal:9094 --throughput 100000 --record-size 1024
    List<String> brokerEndpoints = state.getBrokerEndpoints();
    String brokers = StringUtils.join(brokerEndpoints, ",");
    String bootstrapServers = "bootstrap.servers=" + brokers;

    List<String> cmd = new ArrayList<String>();
    cmd.add(binPath + "kafka-producer-perf-test.sh");
    cmd.add("--topic");
    cmd.add(topicName);
    cmd.add("--num-records");
    cmd.add(Integer.toString(messages));
    cmd.add("--throughput");
    cmd.add("100000");
    cmd.add("--record-size");
    cmd.add("1024");
    cmd.add("--producer-props");
    cmd.add(bootstrapServers);

    return runCmd(cmd);
  }

  public static JSONArray getOffsets(String topicName) throws Exception {
    // e.g. ./kafka-run-class.sh kafka.tools.GetOffsetShell --broker-list ip-10-0-1-71.us-west-2.compute.internal:9092,ip-10-0-1-72.us-west-2.compute.internal:9093,ip-10-0-1-68.us-west-2.compute.internal:9094 --topic topic0 --time -1 --partitions 0

    List<String> brokerEndpoints = state.getBrokerEndpoints();
    String brokers = StringUtils.join(brokerEndpoints, ",");

    List<String> cmd = new ArrayList<String>();
    cmd.add(binPath + "kafka-run-class.sh");
    cmd.add("kafka.tools.GetOffsetShell");
    cmd.add("--topic");
    cmd.add(topicName);
    cmd.add("--time");
    cmd.add("-1");
    cmd.add("--broker-list");
    cmd.add(brokers);

    String stdout = (String) runCmd(cmd).get("stdout");
    return getPartitions(stdout);
  }

  public static JSONObject unavailablePartitions() throws Exception {
    // e.g. ./kafka-topics.sh --zookeeper master.mesos:2181/kafka0 --describe --unavailable-partitions

    List<String> cmd = new ArrayList<String>();
    cmd.add(binPath + "kafka-topics.sh");
    cmd.add("--describe");
    cmd.add("--zookeeper");
    cmd.add(zkPath);
    cmd.add("--unavailable-partitions");

    return runCmd(cmd);
  }

  public static JSONObject underReplicatedPartitions() throws Exception {
    // e.g. ./kafka-topics.sh --zookeeper master.mesos:2181/kafka0 --describe --under-replicate-partitions

    List<String> cmd = new ArrayList<String>();
    cmd.add(binPath + "kafka-topics.sh");
    cmd.add("--describe");
    cmd.add("--zookeeper");
    cmd.add(zkPath);
    cmd.add("--under-replicated-partitions");

    return runCmd(cmd);
  }

  private static JSONArray getPartitions(String offsets) {
    List<JSONObject> partitions = new ArrayList<JSONObject>();

    String lines[] = offsets.split("\\n");
    for (String line : lines) {
      // e.g. topic0:2:33334
      String elements[] = line.trim().split(":");
      String partition = elements[1];
      String offset = elements[2];
      JSONObject part = new JSONObject();
      part.put(partition, offset);
      partitions.add(part);
    }

    return new JSONArray(partitions);
  }

  private static JSONObject runCmd(List<String> cmd) throws Exception {
    ProcessBuilder builder = new ProcessBuilder(cmd);

    StopWatch stopWatch = new StopWatch();
    stopWatch.start();
    Process process = builder.start();
    int exitCode = process.waitFor();
    stopWatch.stop();

    String stdout = streamToString(process.getInputStream());
    String stderr = streamToString(process.getErrorStream());

    if (exitCode == 0) {
      log.info(String.format(
          "Command succeeded in %dms: %s",
          stopWatch.getTime(), StringUtils.join(cmd, " ")));
    } else {
      log.warn(String.format(
          "Command failed with code=%d in %dms: %s",
          exitCode, stopWatch.getTime(), StringUtils.join(cmd, " ")));
      log.warn(String.format("stdout:\n%s", stdout));
      log.warn(String.format("stderr:\n%s", stderr));
    }

    JSONObject obj = new JSONObject();
    obj.put("stdout", stdout);
    obj.put("stderr", stderr);
    obj.put("exit_code", exitCode);

    return obj;
  }

  private static String streamToString(InputStream stream) throws Exception {
    BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
    StringBuilder builder = new StringBuilder();
    String line = null;
    while ( (line = reader.readLine()) != null) {
         builder.append(line);
            builder.append(System.getProperty("line.separator"));
    }

    return builder.toString();
  }
}
