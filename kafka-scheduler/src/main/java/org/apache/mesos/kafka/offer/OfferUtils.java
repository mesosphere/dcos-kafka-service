package org.apache.mesos.kafka.offer;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.mesos.config.ConfigurationService;
import org.apache.mesos.kafka.config.KafkaConfigService;
import org.apache.mesos.kafka.state.KafkaStateService;
import org.apache.mesos.offer.OfferRequirement;

import org.apache.mesos.Protos.Label;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.Resource.DiskInfo;
import org.apache.mesos.Protos.Resource.DiskInfo.Persistence;
import org.apache.mesos.Protos.TaskInfo;

public class OfferUtils {
  private static final Log log = LogFactory.getLog(OfferUtils.class);
  private static KafkaStateService state = KafkaStateService.getStateService();

  public static Integer getNextBrokerId() {
    try {
      List<String> taskNames = state.getTaskNames();

      int brokerId = 0;

      while (taskNames.contains(getBrokerName(brokerId))) {
        brokerId++;
      }

      return brokerId;
    } catch (Exception ex) {
      log.error("Failed to get task names with exception: " + ex);
      return null;
    }
  }

  public static String getBrokerName(int brokerId) {
      return "broker-" + brokerId;
  }

  public static String getConfigName(TaskInfo taskInfo) {
    for (Label label : taskInfo.getLabels().getLabelsList()) {
      if (label.getKey().equals("config_target")) {
        return label.getValue();
      }
    }

    return null;
  }

  public static Integer nameToId(String brokerName) {
    String id = brokerName.substring(brokerName.lastIndexOf("-") + 1);
    return Integer.parseInt(id);
  }

  public static String idToName(Integer brokerId) {
    return "broker-" + Integer.toString(brokerId);
  }

  public static String getPersistenceId(TaskInfo taskInfo) {
    for (Resource resource : taskInfo.getResourcesList()) {
      if (resource.getName().equals("disk")) {
        DiskInfo diskInfo = resource.getDisk();
        if (diskInfo != null) {
          Persistence persistence = diskInfo.getPersistence();
          return persistence.getId();
        }
      }
    }

    return null;
  }
}
