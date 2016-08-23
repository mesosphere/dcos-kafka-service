package com.mesosphere.dcos.kafka.config;

import com.mesosphere.dcos.kafka.state.FrameworkState;
import com.mesosphere.dcos.kafka.test.ConfigTestUtils;
import com.mesosphere.dcos.kafka.test.KafkaTestUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collection;


/**
 * This class tests the ConfigStateValidator.
 */
public class ConfigStateValidatorTest {
    @Mock
    FrameworkState frameworkState;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testKafkaZkUriChangeFails() {
        KafkaConfiguration oldKafkaConfiguration = ConfigTestUtils.getTestKafkaConfiguration();
        KafkaConfiguration newKafkaConfiguration = new KafkaConfiguration(
                true,
                KafkaTestUtils.testKafkaVerName,
                KafkaTestUtils.testKafkaSandboxPath,
                "different-kafka-zk-uri",
                KafkaTestUtils.testMesosZkUri,
                null);

        ConfigStateValidator configStateValidator = new ConfigStateValidator(frameworkState);
        Collection<ConfigStateValidator.ValidationError> errors = configStateValidator.validateKafkaConfigChange(oldKafkaConfiguration, newKafkaConfiguration);
        Assert.assertEquals(1, errors.size());
    }

    @Test
    public void testKafkaZkUriUnchangedSucceeds() {
        KafkaConfiguration oldKafkaConfiguration = ConfigTestUtils.getTestKafkaConfiguration();
        KafkaConfiguration newKafkaConfiguration = oldKafkaConfiguration;

        ConfigStateValidator configStateValidator = new ConfigStateValidator(frameworkState);
        Collection<ConfigStateValidator.ValidationError> errors = configStateValidator.validateKafkaConfigChange(oldKafkaConfiguration, newKafkaConfiguration);
        Assert.assertEquals(0, errors.size());
    }

    @Test
    public void testKafkaFrameworkNameChangeFails() throws ConfigStateValidator.ValidationException {
        ServiceConfiguration oldServiceConfiguration = ConfigTestUtils.getTestServiceConfiguration();
        ServiceConfiguration newServiceConfiguration = new ServiceConfiguration(
                1,
                "bad-framework-name",
                KafkaTestUtils.testUser,
                KafkaTestUtils.testPlacementStrategy,
                KafkaTestUtils.testPhaseStrategy,
                KafkaTestUtils.testRole,
                KafkaTestUtils.testPrincipal);

        ConfigStateValidator configStateValidator = new ConfigStateValidator(frameworkState);
        Collection<ConfigStateValidator.ValidationError> errors = configStateValidator.validateServiceConfigChange(oldServiceConfiguration, newServiceConfiguration);
        Assert.assertEquals(1, errors.size());
    }

    @Test
    public void testKafkaFrameworkNameChangeSucceds() throws ConfigStateValidator.ValidationException {
        ServiceConfiguration oldServiceConfiguration = ConfigTestUtils.getTestServiceConfiguration();
        ServiceConfiguration newServiceConfiguration = oldServiceConfiguration;

        ConfigStateValidator configStateValidator = new ConfigStateValidator(frameworkState);
        Collection<ConfigStateValidator.ValidationError> errors = configStateValidator.validateServiceConfigChange(oldServiceConfiguration, newServiceConfiguration);
        Assert.assertEquals(0, errors.size());
    }
}
