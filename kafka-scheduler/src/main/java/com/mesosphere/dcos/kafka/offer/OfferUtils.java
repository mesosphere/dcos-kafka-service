package com.mesosphere.dcos.kafka.offer;

import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Label;
import org.apache.mesos.Protos.TaskInfo;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class OfferUtils {
  public static String getConfigName(TaskInfo taskInfo) {
    for (Label label : taskInfo.getLabels().getLabelsList()) {
      if (label.getKey().equals(PersistentOfferRequirementProvider.CONFIG_TARGET_KEY)) {
        return label.getValue();
      }
    }

    return null;
  }

  public static String brokerIdToTaskName(Integer brokerId) {
    return "broker-" + Integer.toString(brokerId);
  }

  public static int nameToId(String brokerName) {
    return Integer.parseInt(brokerName.substring(brokerName.indexOf('-') + 1));
  }

  public static Map<String, String> fromEnvironmentToMap(Protos.Environment environment) {
    Map<String, String> map = new HashMap<>();

    final List<Protos.Environment.Variable> variablesList = environment.getVariablesList();

    for (Protos.Environment.Variable variable : variablesList) {
      map.put(variable.getName(), variable.getValue());
    }

    return map;
  }

  public static Protos.Environment environment(Map<String, String> map) {
    Collection<Protos.Environment.Variable> vars = map
            .entrySet()
            .stream()
            .map(entrySet -> Protos.Environment.Variable.newBuilder()
                    .setName(entrySet.getKey())
                    .setValue(entrySet.getValue()).build())
            .collect(Collectors.toList());

    return Protos.Environment.newBuilder().addAllVariables(vars).build();
  }
}
