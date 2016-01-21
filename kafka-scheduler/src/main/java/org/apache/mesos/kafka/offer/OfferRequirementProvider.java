package org.apache.mesos.kafka.offer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.google.common.base.Joiner;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.mesos.config.ConfigurationService;
import org.apache.mesos.kafka.config.KafkaConfigService;
import org.apache.mesos.kafka.state.KafkaStateService;
import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.protobuf.CommandInfoBuilder;
import org.apache.mesos.protobuf.ResourceBuilder;
import org.apache.mesos.protobuf.TaskInfoBuilder;

import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.Resource.DiskInfo;
import org.apache.mesos.Protos.Resource.DiskInfo.Persistence;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.Volume;

public class OfferRequirementProvider {
  private final Log log = LogFactory.getLog(OfferRequirementProvider.class);

  private static int brokerId = 0;
  private ConfigurationService config = KafkaConfigService.getConfigService();
  private KafkaStateService state = KafkaStateService.getStateService();

  public OfferRequirement getNextRequirement() {
    if (belowTargetBrokerCount()) {
      Integer brokerId = getNextBrokerId();
      List<Resource> resources = getResources(); 
      List<TaskInfo> taskInfos = getTaskInfos(brokerId, resources);
      return new OfferRequirement(taskInfos);
    } else {
      try{
        List<TaskInfo> terminatedTaskInfos = state.getTerminatedTaskInfos();
        if (terminatedTaskInfos.size() > 0) {
          return new OfferRequirement(Arrays.asList(terminatedTaskInfos.get(0)));
        }
      } catch (Exception ex) {
        log.error("Failed to get terminated task list with exception: " + ex);
        return null;
      }
    }

    return null;
  }

  private boolean belowTargetBrokerCount() {
    int targetBrokerCount = Integer.parseInt(config.get("BROKER_COUNT"));
    int currentBrokerCount = 0;

    try {
      currentBrokerCount = state.getTaskNames().size();
    } catch(Exception ex) {
      log.error("Failed to retrieve current broker count with exception: " + ex);
      return false;
    }

    if (currentBrokerCount < targetBrokerCount) {
      return true;
    } else {
      return false;
    }
  }

  private Integer getNextBrokerId() {
    try {
      List<String> taskNames = state.getTaskNames();

      int targetBrokerCount = Integer.parseInt(config.get("BROKER_COUNT"));
      for (int i=0; i<targetBrokerCount; i++) {
        String brokerName = "broker-" + i;
        if (!taskNames.contains(brokerName)) {
          return i;
        }
      }
    } catch (Exception ex) {
      log.error("Failed to get task names with exception: " + ex);
      return null;
    }

    return null;
  }

  private List<Resource> getResources() {
    boolean persistentVolumesEnabled = Boolean.parseBoolean(config.get("BROKER_PV"));

    if (persistentVolumesEnabled) {
      return getPersistentVolumeResources();
    } else {
      return getSandboxResources();
    } 
  }

  private List<Resource> getSandboxResources() {
    Resource cpus = ResourceBuilder.cpus(getBrokerCpus());
    Resource mem = ResourceBuilder.mem(getBrokerMem());
    return Arrays.asList(cpus, mem);
  }

  private List<Resource> getPersistentVolumeResources() {
    Resource cpus = ResourceBuilder.reservedCpus(getBrokerCpus(), getRole(), getPrincipal());
    Resource mem = ResourceBuilder.reservedMem(getBrokerMem(), getRole(), getPrincipal());
    Resource volume = getVolumeResource();
    return Arrays.asList(cpus, mem, volume);
  }

  private Resource getVolumeResource() {
    Resource resource = ResourceBuilder.reservedDisk(getBrokerDisk(), getRole(), getPrincipal());
    Resource.Builder builder = Resource.newBuilder(resource);
    String persistenceId = UUID.randomUUID().toString();
    String volumePath = "kafka-volume-" + persistenceId;
    DiskInfo diskInfo = createDiskInfo(persistenceId, volumePath);
    builder.setDisk(diskInfo);

    return builder.build();
  }

  private static DiskInfo createDiskInfo(String persistenceId, String containerPath) {
    return DiskInfo.newBuilder()
      .setPersistence(Persistence.newBuilder()
          .setId(persistenceId))
      .setVolume(Volume.newBuilder()
          .setMode(Volume.Mode.RW)
          .setContainerPath(containerPath))
      .build();
  }

  private String getRole() {
    return config.get("ROLE");
  }

  private String getPrincipal() {
    return config.get("PRINCIPAL");
  }

  private double getBrokerCpus() {
    return getConfigDouble("BROKER_CPUS");
  }

  private double getBrokerMem() {
    return getConfigDouble("BROKER_MEM");
  }

  private double getBrokerDisk() {
    return getConfigDouble("BROKER_DISK");
  }

  private double getConfigDouble(String key) {
    return Double.parseDouble(config.get(key));
  }

  private List<TaskInfo> getTaskInfos(int brokerId, List<Resource> resources) {
    String brokerName = "broker-" + brokerId;
    String taskId = brokerName + "__" + UUID.randomUUID();
    int port = 9092 + brokerId;

    List<String> commands = new ArrayList<>();

    // Do not use the /bin/bash-specific "source"
    commands.add(". $MESOS_SANDBOX/container-hook/container-hook.sh");

    // Export the JRE and log the environment 
    commands.add("export PATH=$PATH:$MESOS_SANDBOX/jre/bin");
    commands.add("env");

    // Run Kafka
    String kafkaStartCmd = String.format(
          "$MESOS_SANDBOX/%1$s/bin/kafka-server-start.sh "
        + "$MESOS_SANDBOX/%1$s/config/server.properties "
        + "--override zookeeper.connect=%2$s/%3$s "
        + "--override broker.id=%4$s "
        + "--override log.dirs=$MESOS_SANDBOX/kafka-logs "
        + "--override port=%5$d "
        + "--override listeners=PLAINTEXT://:%5$d "
        + "$CONTAINER_HOOK_FLAGS",
        config.get("KAFKA_VER_NAME"), // #1
        config.get("ZOOKEEPER_ADDR"), // #2
        config.get("FRAMEWORK_NAME"), // #3
        brokerId, // #4
        port); // #5

    commands.add(kafkaStartCmd);

    String command = Joiner.on(" && ").join(commands);

    return Arrays.asList(new TaskInfoBuilder(taskId, brokerName, "" /* slaveId */)
        .addAllResources(resources)
        .addResource(ResourceBuilder.ports(port, port))
        .setCommand(new CommandInfoBuilder()
            .addUri(config.get("KAFKA_URI"))
            .addUri(config.get("CONTAINER_HOOK_URI"))
            .addUri(config.get("JAVA_URI"))
            .setCommand(command)
            .build())
        .build());
  }
}
