package org.apache.mesos.kafka.offer;

import com.google.common.base.Joiner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.mesos.config.ConfigurationService;
import org.apache.mesos.kafka.config.KafkaConfigService;
import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.offer.OfferRequirement.VolumeMode;
import org.apache.mesos.protobuf.CommandInfoBuilder;
import org.apache.mesos.protobuf.ResourceBuilder;
import org.apache.mesos.protobuf.TaskInfoBuilder;

import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.Resource.DiskInfo;
import org.apache.mesos.Protos.Resource.DiskInfo.Persistence;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.Volume;

public class PersistentOfferRequirementProvider implements OfferRequirementProvider {
  private final Log log = LogFactory.getLog(PersistentOfferRequirementProvider.class);
  private ConfigurationService config = KafkaConfigService.getConfigService();
  private PlacementStrategyService placementSvc = PlacementStrategy.getPlacementStrategyService();

  public OfferRequirement getNewOfferRequirement() {
    Integer brokerId = OfferUtils.getNextBrokerId();
    if (brokerId == null) {
      log.error("Failed to get broker ID");
      return null;
    }

    TaskInfo taskInfo = getTaskInfo(brokerId);
    return new OfferRequirement(
        getRole(),
        getPrincipal(),
        Arrays.asList(taskInfo),
        placementSvc.getAgentsToAvoid(taskInfo),
        placementSvc.getAgentsToColocate(taskInfo),
        VolumeMode.CREATE);
  }

  public OfferRequirement getReplacementOfferRequirement(TaskInfo taskInfo) {
    return new OfferRequirement(
        getRole(),
        getPrincipal(),
        Arrays.asList(taskInfo),
        null,
        null,
        VolumeMode.EXISTING);
  }

  private String getRole() {
    return config.get("ROLE");
  }

  private String getPrincipal() {
    return config.get("PRINCIPAL");
  }

  private TaskInfo getTaskInfo(int brokerId) {
    String brokerName = "broker-" + brokerId;
    String taskId = brokerName + "__" + UUID.randomUUID();
    int port = 9092 + brokerId;

    List<String> commands = new ArrayList<>();

    // Do not use the /bin/bash-specific "source"
    commands.add(". $MESOS_SANDBOX/container-hook/container-hook.sh");

    // Export the JRE and log the environment 
    commands.add("export PATH=$PATH:$MESOS_SANDBOX/jre/bin");
    commands.add("env");

    String containerPath = "kafka-volume-" + UUID.randomUUID();

    // Run Kafka
    String kafkaStartCmd = String.format(
        "$MESOS_SANDBOX/%1$s/bin/kafka-server-start.sh "
        + "$MESOS_SANDBOX/%1$s/config/server.properties "
        + "--override zookeeper.connect=%2$s/%3$s "
        + "--override broker.id=%4$s "
        + "--override log.dirs=$MESOS_SANDBOX/%6$s "
        + "--override port=%5$d "
        + "--override listeners=PLAINTEXT://:%5$d "
        + "$CONTAINER_HOOK_FLAGS",
        config.get("KAFKA_VER_NAME"), // #1
        config.get("ZOOKEEPER_ADDR"), // #2
        config.get("FRAMEWORK_NAME"), // #3
        brokerId, // #4
        port,
        containerPath); // #5

    commands.add(kafkaStartCmd);

    String command = Joiner.on(" && ").join(commands);

    double cpus = Double.parseDouble(config.get("BROKER_CPUS"));
    double mem = Double.parseDouble(config.get("BROKER_MEM"));
    String role = getRole();
    String principal = getPrincipal();

    return new TaskInfoBuilder(taskId, brokerName, "" /* slaveId */)
        .addResource(ResourceBuilder.reservedCpus(cpus, role, principal))
        .addResource(ResourceBuilder.reservedMem(mem, role, principal))
        .addResource(getVolumeResource(containerPath))
        .addResource(ResourceBuilder.ports(port, port))
        .setCommand(new CommandInfoBuilder()
          .addUri(config.get("KAFKA_URI"))
          .addUri(config.get("CONTAINER_HOOK_URI"))
          .addUri(config.get("JAVA_URI"))
          .setCommand(command)
          .build())
        .build();
  }

  private Resource getVolumeResource(String containerPath) {
    String persistenceId = UUID.randomUUID().toString();
    DiskInfo diskInfo = createDiskInfo(persistenceId, containerPath);

    double disk = Double.parseDouble(config.get("BROKER_DISK"));
    Resource resource = ResourceBuilder.reservedDisk(disk, getRole(), getPrincipal());
    Resource.Builder builder = Resource.newBuilder(resource);
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
}
