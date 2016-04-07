package org.apache.mesos.kafka.offer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mesos.Protos.Label;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.Resource.DiskInfo;
import org.apache.mesos.Protos.Resource.DiskInfo.Persistence;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.Value;
import org.apache.mesos.kafka.state.KafkaStateService;

import java.util.List;

public class OfferUtils {
  private static final Log log = LogFactory.getLog(OfferUtils.class);

  public static Integer getNextBrokerId(KafkaStateService state) {
    try {
      List<String> taskNames = state.getTaskNames();

      int brokerId = 0;

      while (taskNames.contains(idToName(brokerId))) {
        brokerId++;
      }

      return brokerId;
    } catch (Exception ex) {
      log.error("Failed to get task names with exception: " + ex);
      return null;
    }
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

  public static String getTaskName(String taskId) {
    int underScoreIndex = taskId.indexOf("__");
    return taskId.substring(0, underScoreIndex);
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

    log.warn("Returning null persistenceId for taskInfo: " + taskInfo);
    return null;
  }

  @SuppressWarnings("PMD")
  public static Long getPort(TaskInfo taskInfo) {
    for (Resource resource : taskInfo.getResourcesList()) {
      if (resource.getName().equals("ports")) {
        Value.Ranges ranges = resource.getRanges();
        for (Value.Range range : ranges.getRangeList()) {
          return range.getBegin();
        }
      }
    }

    log.warn("Returning null port for taskInfo: " + taskInfo);
    return null;
  }
}
