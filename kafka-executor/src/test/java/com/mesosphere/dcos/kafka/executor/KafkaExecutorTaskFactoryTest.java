package com.mesosphere.dcos.kafka.executor;

import com.mesosphere.dcos.kafka.commons.KafkaTask;
import org.apache.mesos.executor.ExecutorTask;
import org.apache.mesos.executor.ExecutorTaskException;
import org.apache.mesos.executor.ProcessTask;
import org.junit.Assert;
import org.junit.Test;

public class KafkaExecutorTaskFactoryTest {
    @Test
    public void testSanity() throws Exception{
        final KafkaExecutorTaskFactory kafkaExecutorTaskFactory = new KafkaExecutorTaskFactory();
        final ExecutorTask task = kafkaExecutorTaskFactory.createTask(KafkaTask.BROKER.name(), null, null);

        Assert.assertNotNull(task);
        Assert.assertTrue(task instanceof ProcessTask);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUnknownTask() throws Exception{
        final KafkaExecutorTaskFactory kafkaExecutorTaskFactory = new KafkaExecutorTaskFactory();
        kafkaExecutorTaskFactory.createTask("foo", null, null);
    }

    @Test(expected = ExecutorTaskException.class)
    public void testTaskDispatchRuleNotFound() throws Exception {
        final KafkaExecutorTaskFactory kafkaExecutorTaskFactory = new KafkaExecutorTaskFactory();
        kafkaExecutorTaskFactory.createTask(KafkaTask.LOG_CONFIG.name(), null, null);
    }
}
