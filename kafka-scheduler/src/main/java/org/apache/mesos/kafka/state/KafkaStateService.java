package org.apache.mesos.kafka.state;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.curator.framework.CuratorFramework;

import org.apache.mesos.kafka.config.KafkaConfigService;
import org.apache.mesos.kafka.offer.OfferUtils;

import org.apache.mesos.Protos.FrameworkID;
import org.apache.mesos.Protos.TaskID;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.TaskState;
import org.apache.mesos.Protos.TaskStatus;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

public class KafkaStateService implements Observer {
  private final Log log = LogFactory.getLog(KafkaStateService.class);
  private final CuratorFramework zkClient;

  private static KafkaConfigService config = KafkaConfigService.getEnvConfig();

  private static String zkRoot;
  private static String stateRoot;
  private static String taskPath;
  private static String fwkIdPath;

  private static KafkaStateService stateService = null;

  private KafkaStateService() {
    zkClient = KafkaStateUtils.createZkClient(config.getZookeeperAddress());

    zkRoot = "/" + config.get("FRAMEWORK_NAME");
    stateRoot = zkRoot + "/state";
    taskPath = stateRoot + "/tasks";
    fwkIdPath = stateRoot + "/framework-id";

    try {
      initializePath(stateRoot + "/tasks");
      initializePath(stateRoot + "/framework-id");
    } catch(Exception ex) {
      log.fatal("Failed with exception: " + ex);
    }
  }

  public static KafkaStateService getStateService() {
    if (stateService == null) {
      stateService = new KafkaStateService();
    }

    return stateService;
  }

  public FrameworkID getFrameworkId() {
    try {
      byte[] bytes = zkClient.getData().forPath(fwkIdPath);
      if (bytes.length > 0) {
        return FrameworkID.parseFrom(bytes);
      }
    } catch(Exception ex) {
      log.error("Failed to get FrameworkID with exception: " + ex);
    }

    return null;
  }

  public void setFrameworkId(FrameworkID fwkId) {
    try {
      zkClient.setData().forPath(fwkIdPath, fwkId.toByteArray());
    } catch(Exception ex) {
      log.error("Failed to set FrameworkID: " + fwkId + " with exception: " + ex);
    }
  }

  public void recordTasks(List<TaskInfo> taskInfos) throws Exception {
    recordTaskInfos(taskInfos);
    List<TaskStatus> taskStatuses = taskInfosToTaskStatuses(taskInfos);
    recordTaskStatuses(taskStatuses);
  }

  public void update(Observable observable, Object obj) {
    TaskStatus taskStatus = (TaskStatus) obj;

    try {
      recordTaskStatus(taskStatus);
    } catch (Exception ex) {
      log.error("Failed to update TaskStatus: " + taskStatus + "with exception: " + ex);
    }
  }

  public List<TaskInfo> getTerminatedTaskInfos() throws Exception {
    List<TaskInfo> taskInfos = new ArrayList<TaskInfo>();

    for (String taskId : getTaskIds()) {
      TaskStatus taskStatus = getTaskStatus(taskId);
      if (isTerminated(taskStatus)) {
        taskInfos.add(getTaskInfo(taskId));
      }
    }

    return taskInfos;
  }

  public TaskStatus getTaskStatus(String taskId) throws Exception {
    String path = getTaskStatusPath(taskId);
    byte[] bytes = zkClient.getData().forPath(path);
    return TaskStatus.parseFrom(bytes);
  }

  public List<TaskStatus> getTaskStatuses() throws Exception {
    List<TaskStatus> taskStatuses = new ArrayList<TaskStatus>();

    List<String> taskIds = getTaskIds();
    for (String taskId : taskIds) {
      taskStatuses.add(getTaskStatus(taskId));
    }

    return taskStatuses;
  }

  public TaskInfo getTaskInfo(String taskId) throws Exception {
    String path = getTaskInfoPath(taskId);
    byte[] bytes = zkClient.getData().forPath(path);
    return TaskInfo.parseFrom(bytes);
  }

  public List<String> getTaskNames() throws Exception {
    List<String> taskNames = new ArrayList<String>();

    List<TaskInfo> taskInfos = getTaskInfos();
    for (TaskInfo taskInfo : taskInfos) {
      taskNames.add(taskInfo.getName());
    }

    return taskNames;
  }

  public List<TaskInfo> getTaskInfos() throws Exception {
    List<TaskInfo> taskInfos = new ArrayList<TaskInfo>();
    for (String taskId : getTaskIds()) {
        taskInfos.add(getTaskInfo(taskId));
    }

    return taskInfos;
  }

  public String getTaskIdForBroker(Integer brokerId) throws Exception {
    String brokerName = OfferUtils.idToName(brokerId);

    for (TaskInfo taskInfo : getTaskInfos()) {
      if (taskInfo.getName().equals(brokerName)) {
        return taskInfo.getTaskId().getValue();
      }
    }

    return null;
  }

  public JSONArray getBrokerIds() throws Exception {
    return getIds(zkRoot + "/brokers/ids");
  }

  public JSONObject getBroker(String id) throws Exception {
    return getElement(zkRoot + "/brokers/ids/" + id);
  }

  public List<String> getBrokerEndpoints() throws Exception {
    String brokerPath = zkRoot + "/brokers/ids";
    List<String> endpoints = new ArrayList<String>();

    List<String> ids = zkClient.getChildren().forPath(brokerPath);
    for (String id : ids) {
      byte[] bytes = zkClient.getData().forPath(brokerPath + "/" + id);
      JSONObject broker = new JSONObject(new String(bytes, "UTF-8"));
      String host = (String) broker.get("host");
      Integer port = (Integer) broker.get("port");
      endpoints.add(host + ":" + port);
    }

    return endpoints;
  }

  public JSONArray getTopics() throws Exception {
    return getIds(zkRoot + "/brokers/topics");
  }

  public JSONObject getTopic(String topicName) throws Exception {
    String partitionsPath = zkRoot + "/brokers/topics/" + topicName + "/partitions";
    List<String> partitionIds = zkClient.getChildren().forPath(partitionsPath);

    List<JSONObject> partitions = new ArrayList<JSONObject>();
    for (String partitionId : partitionIds) {
      JSONObject state = getElement(partitionsPath + "/" + partitionId + "/state");
      JSONObject partition = new JSONObject();
      partition.put(partitionId, state);
      partitions.add(partition);
    }

    JSONObject obj = new JSONObject();
    obj.put("partitions", partitions);
    return obj;
  }

  private JSONArray getIds(String path) throws Exception {
    List<String> ids = zkClient.getChildren().forPath(path);
    return new JSONArray(ids);
  }

  private JSONObject getElement(String path) throws Exception {
    byte[] bytes = zkClient.getData().forPath(path);
    String element = new String(bytes, "UTF-8");
    return new JSONObject(element);
  }

  private boolean isTerminated(TaskStatus taskStatus) {
    TaskState taskState = taskStatus.getState();
    return taskState.equals(TaskState.TASK_FINISHED) ||
      taskState.equals(TaskState.TASK_FAILED) ||
      taskState.equals(TaskState.TASK_KILLED) ||
      taskState.equals(TaskState.TASK_LOST) ||
      taskState.equals(TaskState.TASK_ERROR);
  }

  public List<String> getTaskIds() throws Exception {
    return zkClient.getChildren().forPath(taskPath);
  }

  private String getTaskRootPath(String taskId) {
    return taskPath + "/" + taskId;
  }

  private String getTaskInfoPath(String taskId) {
    return getTaskRootPath(taskId) + "/info";
  }

  private String getTaskStatusPath(String taskId) {
    return getTaskRootPath(taskId) + "/status";
  }

  private void record(String path, byte[] bytes) throws Exception {
    if (zkClient.checkExists().forPath(path) == null) {
      zkClient.create().creatingParentsIfNeeded().forPath(path, bytes);
    } else {
      zkClient.setData().forPath(path, bytes);
    }
  }

  public void recordTaskInfo(TaskInfo taskInfo) throws Exception {
    String infoPath = getTaskInfoPath(taskInfo.getTaskId().getValue());
    record(infoPath, taskInfo.toByteArray());
  }

  private void recordTaskInfos(List<TaskInfo> taskInfos) throws Exception {
    for (TaskInfo taskInfo : taskInfos) {
      recordTaskInfo(taskInfo);
    }
  }

  private void recordTaskStatus(TaskStatus taskStatus) throws Exception {
    String statusPath = getTaskStatusPath(taskStatus.getTaskId().getValue());
    if (zkClient.checkExists().forPath(statusPath) == null &&
        isTerminated(taskStatus)) {
          log.warn("Dropping status update because the ZK path doesn't exist and it's a termianl Status: " + taskStatus);
    } else {
      record(statusPath, taskStatus.toByteArray());
    }
  }

  public void deleteTask(String taskId) {
    try {
      String pathToDelete = getTaskRootPath(taskId);
      log.info("Deleting path: " + pathToDelete);
      zkClient.delete().deletingChildrenIfNeeded().forPath(pathToDelete);
    } catch(Exception ex) {
      log.error("Failed to delete Task: " + taskId + " with exception: " + ex);
    }
  }

  private void recordTaskStatuses(List<TaskStatus> taskStatuses) throws Exception {
    for (TaskStatus taskStatus : taskStatuses) {
      recordTaskStatus(taskStatus);
    }
  }

  private TaskStatus taskInfoToTaskStatus(TaskInfo taskInfo) {
      TaskID taskId = taskInfo.getTaskId();

      TaskStatus.Builder taskBuilder = TaskStatus.newBuilder();
      taskBuilder.setTaskId(taskId);
      taskBuilder.setState(TaskState.TASK_STAGING);
      return taskBuilder.build();
  }

  private List<TaskStatus> taskInfosToTaskStatuses(List<TaskInfo> taskInfos) {
    List<TaskStatus> taskStatuses = new ArrayList<TaskStatus>();

    for (TaskInfo taskInfo : taskInfos) {
      taskStatuses.add(taskInfoToTaskStatus(taskInfo));
    }

    return taskStatuses;
  }

  private void initializePath(String path) throws Exception {
    if (zkClient.checkExists().forPath(path) == null) {
      zkClient.create().creatingParentsIfNeeded().forPath(path, new byte[0]);
    }
  }
}
