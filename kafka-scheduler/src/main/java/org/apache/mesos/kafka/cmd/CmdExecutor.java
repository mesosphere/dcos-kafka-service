package org.apache.mesos.kafka.cmd;

import java.util.ArrayList;
import java.util.List;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.apache.mesos.config.ConfigurationService;
import org.apache.mesos.config.FrameworkConfigurationService;
import org.apache.mesos.kafka.state.KafkaStateService;
import org.apache.mesos.kafka.config.KafkaConfigService;

import org.json.JSONObject;

public class CmdExecutor {
  private static KafkaConfigService config = KafkaConfigService.getConfigService();
  private static KafkaStateService state = KafkaStateService.getStateService();

  private static String binPath = config.get("MESOS_SANDBOX") + "/" + config.get("KAFKA_BIN_PATH") + "/";
  private static String zkPath = config.getKafkaZkUri(); 

  public static JSONObject createTopic(String name, int partitionCount, int replicationFactor) throws Exception {
    // e.g. ./kafka-topics.sh --create --zookeeper master.mesos:2181/kafka-0 --topic topic0 --partitions 3 --replication-factor 3

    
    List<String> cmd = new ArrayList<String>();
    cmd.add(binPath + "kafka-topics.sh");
    cmd.add("--create");
    cmd.add("--zookeeper ");
    cmd.add(zkPath);
    cmd.add("--topic ");
    cmd.add(name);
    cmd.add("--partitions ");
    cmd.add(Integer.toString(partitionCount));
    cmd.add("--replication-factor");
    cmd.add(Integer.toString(replicationFactor));

    return runCmd(cmd); 
  }

  private static JSONObject runCmd(List<String> cmd) throws Exception{
    ProcessBuilder builder = new ProcessBuilder(cmd);
    Process process = builder.start();
    int exitCode = process.waitFor();

    String stdout = streamToString(process.getInputStream());
    String stderr = streamToString(process.getErrorStream());

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
