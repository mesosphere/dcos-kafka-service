package org.apache.mesos.kafka.offer;

import org.apache.mesos.Protos.Label;
import org.apache.mesos.Protos.TaskInfo;

public class OfferUtils {
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
}
