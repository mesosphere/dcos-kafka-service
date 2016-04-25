package org.apache.mesos.kafka.state;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.curator.framework.CuratorFramework;
import org.apache.mesos.Protos.FrameworkID;
import org.apache.mesos.Protos.TaskID;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.TaskState;
import org.apache.mesos.Protos.TaskStatus;
import org.apache.mesos.kafka.offer.OfferUtils;
import org.apache.mesos.reconciliation.TaskStatusProvider;
import org.apache.zookeeper.KeeperException.NoNodeException;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;

/**
 * Read/write interface for storing and retrieving information about Kafka tasks.
 */
public class KafkaStateService implements Observer, TaskStatusProvider {
  private static final Log log = LogFactory.getLog(KafkaStateService.class);

  private final CuratorFramework zkClient;
  private final String zkRoot;
  private final String stateRoot;
  private final String taskPath;
  private final String fwkIdPath;

  public KafkaStateService(String zkHost, String zkRoot) {
    zkClient = KafkaStateUtils.createZkClient(zkHost);
    this.zkRoot = zkRoot;
    stateRoot = zkRoot + "/state";
    taskPath = stateRoot + "/tasks";
    fwkIdPath = stateRoot + "/framework-id";

    // Create framework-owned paths, if needed
    try {
      initializePath(taskPath);
      initializePath(fwkIdPath);
    } catch (Exception ex) {
      log.fatal("Failed with exception: " + ex);
    }
  }

  public FrameworkID getFrameworkId() {
    try {
      byte[] bytes = zkClient.getData().forPath(fwkIdPath);
      if (bytes.length > 0) {
        return FrameworkID.parseFrom(bytes);
      }
    } catch (Exception ex) {
      log.error("Failed to get FrameworkID with exception: " + ex);
    }

    return null;
  }

  public void setFrameworkId(FrameworkID fwkId) {
    try {
      zkClient.setData().forPath(fwkIdPath, fwkId.toByteArray());
    } catch (Exception ex) {
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

    for (String taskName : getTaskNames()) {
      TaskStatus taskStatus = getTaskStatus(taskName);
      if (isTerminated(taskStatus)) {
        taskInfos.add(getTaskInfo(taskName));
      }
    }

    return taskInfos;
  }

  public int getRunningBrokersCount() throws Exception {
    int count = 0;

    for (String taskName : getTaskNames()) {
      TaskStatus taskStatus = getTaskStatus(taskName);
      if (isRunning(taskStatus)) {
        count++;
      }
    }

    return count;
  }

  public TaskStatus getTaskStatus(String taskName) throws Exception {
    String path = getTaskStatusPath(taskName);
    byte[] bytes = zkClient.getData().forPath(path);
    return TaskStatus.parseFrom(bytes);
  }

  @Override
  public Set<TaskStatus> getTaskStatuses() throws Exception {
    Set<TaskStatus> taskStatuses = new HashSet<TaskStatus>();

    for (String taskName : getTaskNames()) {
      taskStatuses.add(getTaskStatus(taskName));
    }

    return taskStatuses;
  }

  public TaskInfo getTaskInfo(String taskName) throws Exception {
    String path = getTaskInfoPath(taskName);
    byte[] bytes = zkClient.getData().forPath(path);
    return TaskInfo.parseFrom(bytes);
  }

  public List<TaskInfo> getTaskInfos() throws Exception {
    List<TaskInfo> taskInfos = new ArrayList<TaskInfo>();
    for (String taskId : getTaskNames()) {
      taskInfos.add(getTaskInfo(taskId));
    }

    return taskInfos;
  }

  public List<TaskID> getTaskIds() throws Exception {
    List<TaskID> taskIds = new ArrayList<TaskID>();

    for (TaskInfo taskInfo : getTaskInfos()) {
      taskIds.add(taskInfo.getTaskId());
    }

    return taskIds;
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

  public List<String> getBrokerDNSEndpoints(String frameworkName) throws Exception {
    String brokerPath = zkRoot + "/brokers/ids";
    List<String> endpoints = new ArrayList<String>();

    List<String> ids = zkClient.getChildren().forPath(brokerPath);
    for (String id : ids) {
      byte[] bytes = zkClient.getData().forPath(brokerPath + "/" + id);
      JSONObject broker = new JSONObject(new String(bytes, "UTF-8"));
      String host = "broker-" + id + "." + frameworkName + ".mesos";
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
    try {
      return new JSONArray(zkClient.getChildren().forPath(path));
    } catch (NoNodeException e) {
      log.info("List path " + path + " doesn't exist, returning empty list. Kafka not running yet?", e);
      return new JSONArray();
    }
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

  private boolean isRunning(TaskStatus taskStatus) {
    TaskState taskState = taskStatus.getState();
    return taskState.equals(TaskState.TASK_RUNNING);
  }

  public List<String> getTaskNames() throws Exception {
    return zkClient.getChildren().forPath(taskPath);
  }

  private String getTaskRootPath(String taskName) {
    return taskPath + "/" + taskName;
  }

  private String getTaskInfoPath(String taskName) {
    return getTaskRootPath(taskName) + "/info";
  }

  private String getTaskStatusPath(String taskName) {
    return getTaskRootPath(taskName) + "/status";
  }

  private void record(String path, byte[] bytes) throws Exception {
    if (zkClient.checkExists().forPath(path) == null) {
      zkClient.create().creatingParentsIfNeeded().forPath(path, bytes);
    } else {
      zkClient.setData().forPath(path, bytes);
    }
  }

  public void recordTaskInfo(TaskInfo taskInfo) throws Exception {
    String infoPath = getTaskInfoPath(taskInfo.getName());
    record(infoPath, taskInfo.toByteArray());
  }

  private void recordTaskInfos(List<TaskInfo> taskInfos) throws Exception {
    for (TaskInfo taskInfo : taskInfos) {
      recordTaskInfo(taskInfo);
    }
  }

  private void recordTaskStatus(TaskStatus taskStatus) throws Exception {
    String statusPath = getTaskStatusPath(OfferUtils.getTaskName(taskStatus.getTaskId().getValue()));
    if (zkClient.checkExists().forPath(statusPath) == null &&
      !taskStatus.getState().equals(TaskState.TASK_STAGING)) {
      log.warn("Dropping status update because the ZK path doesn't exist and Status is not STAGING: " + taskStatus);
    } else {
      record(statusPath, taskStatus.toByteArray());
    }
  }

  public void deleteTask(String taskId) {
    try {
      String taskName = OfferUtils.getTaskName(taskId);
      String pathToDelete = getTaskRootPath(taskName);
      log.info("Deleting path: " + pathToDelete);
      zkClient.delete().deletingChildrenIfNeeded().forPath(pathToDelete);
    } catch (Exception ex) {
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
