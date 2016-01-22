package org.apache.mesos.kafka.state;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.RetryPolicy;
import org.apache.curator.retry.ExponentialBackoffRetry;

import org.apache.mesos.config.ConfigurationService;
import org.apache.mesos.kafka.config.KafkaConfigService;

import org.apache.mesos.Protos.FrameworkID;
import org.apache.mesos.Protos.TaskID;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.TaskState;
import org.apache.mesos.Protos.TaskStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

public class KafkaStateService implements Observer {
  private final Log log = LogFactory.getLog(KafkaStateService.class);
  private final CuratorFramework zkClient;
  
  private static ConfigurationService config = KafkaConfigService.getConfigService();

  private static final Integer POLL_DELAY_MS = 1000;
  private static final Integer CURATOR_MAX_RETRIES = 3;
  private static String zkRoot;
  private static String taskPath;
  private static String fwkIdPath;

  private static KafkaStateService stateService = null;

  private KafkaStateService() {
    zkClient = createCuratorClient();

    zkRoot = "/" + config.get("FRAMEWORK_NAME") + "/state";
    taskPath = zkRoot + "/tasks";
    fwkIdPath = zkRoot + "/framework-id";

    try {
      initializePath(zkRoot + "/tasks");
      initializePath(zkRoot + "/framework-id");
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
    FrameworkID fwkId = null;
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

  private boolean isTerminated(TaskStatus taskStatus) {
    TaskState taskState = taskStatus.getState();
    return taskState.equals(TaskState.TASK_FINISHED) ||
      taskState.equals(TaskState.TASK_FAILED) ||
      taskState.equals(TaskState.TASK_KILLED) ||
      taskState.equals(TaskState.TASK_LOST) ||
      taskState.equals(TaskState.TASK_ERROR);
  }

  private List<String> getTaskIds() throws Exception {
    return zkClient.getChildren().forPath(taskPath);
  }

  private String getTaskInfoPath(String taskId) {
    return taskPath + "/" + taskId + "/info";
  }

  private String getTaskStatusPath(String taskId) {
    return taskPath + "/" + taskId + "/status";
  }

  private void record(String path, byte[] bytes) throws Exception {
    if (zkClient.checkExists().forPath(path) == null) {
      zkClient.create().creatingParentsIfNeeded().forPath(path, bytes);
    } else {
      zkClient.setData().forPath(path, bytes);
    }
  }

  private void recordTaskInfo(TaskInfo taskInfo) throws Exception {
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
    record(statusPath, taskStatus.toByteArray());
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

  private CuratorFramework createCuratorClient() {
    String hosts = config.get("ZOOKEEPER_ADDR");
    RetryPolicy retryPolicy = new ExponentialBackoffRetry(POLL_DELAY_MS, CURATOR_MAX_RETRIES);

    CuratorFramework client = CuratorFrameworkFactory.newClient(hosts, retryPolicy);
    client.start();

    return client;
  }
} 
