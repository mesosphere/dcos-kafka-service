package org.apache.mesos.kafka.config;

import org.apache.mesos.config.ConfigStoreException;
import org.apache.mesos.config.ConfigurationFactory;
import org.yaml.snakeyaml.Yaml;

import java.nio.charset.StandardCharsets;

public class KafkaConfigurationFactory implements ConfigurationFactory<KafkaSchedulerConfiguration> {
    @Override
    public KafkaSchedulerConfiguration parse(byte[] bytes) throws ConfigStoreException {
        try {
            final String yamlStr = new String(bytes, StandardCharsets.UTF_8);

            final Yaml yaml = new Yaml();
            return yaml.loadAs(yamlStr, KafkaSchedulerConfiguration.class);
        } catch (Exception e) {
            throw new ConfigStoreException(e);
        }
    }
}
