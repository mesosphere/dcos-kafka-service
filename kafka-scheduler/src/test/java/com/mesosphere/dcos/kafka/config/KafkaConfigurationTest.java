package com.mesosphere.dcos.kafka.config;

import static org.junit.Assert.*;

import org.junit.Test;
import java.util.HashMap;
import java.util.Map;

/**
 * This class tests the KafkaConfiguration class.
 */
public class KafkaConfigurationTest {

    private static final String VALUE = "foo";

    @Test
    public void testConstructEmptyConstructor() throws Exception {
        KafkaConfiguration config = new KafkaConfiguration() {
            @Override
            protected Map<String, String> getSystemEnv() {
                return mapWithEntry("KAFKA_OVERRIDE_SOME_KAFKA_CONFIG", VALUE);
            }
        };
        assertEquals(VALUE, config.getOverrides().get("some.kafka.config"));
    }

    @Test
    public void testConstructEmptyOverrides() throws Exception {
        KafkaConfiguration config = new KafkaConfiguration(false, "", "", "", "", new HashMap<>()) {
            @Override
            protected Map<String, String> getSystemEnv() {
                return mapWithEntry("KAFKA_OVERRIDE_SOME_KAFKA_CONFIG", VALUE);
            }
        };
        assertEquals(VALUE, config.getOverrides().get("some.kafka.config"));
    }

    @Test
    public void testConstructNullOverrides() throws Exception {
        KafkaConfiguration config = new KafkaConfiguration(false, "", "", "", "", null) {
            @Override
            protected Map<String, String> getSystemEnv() {
                return mapWithEntry("KAFKA_OVERRIDE_SOME_KAFKA_CONFIG", VALUE);
            }
        };
        assertEquals(VALUE, config.getOverrides().get("some.kafka.config"));
    }

    // TODO(nick): Remove this test when the conversion is removed in Jan 2017.
    @Test
    public void testConstructOldOverrideKey() throws Exception {
        KafkaConfiguration config = new KafkaConfiguration(
                false, "", "", "", "", mapWithEntry("someKafkaConfig", VALUE));
        assertEquals(VALUE, config.getOverrides().get("some.kafka.config"));
    }

    @Test
    public void testConstructNewOverrideKey() throws Exception {
        KafkaConfiguration config = new KafkaConfiguration(
                false, "", "", "", "", mapWithEntry("some.kafka.config", VALUE));
        assertEquals(VALUE, config.getOverrides().get("some.kafka.config"));
    }

    @Test
    public void testSetNullOverrides() throws Exception {
        KafkaConfiguration config = new KafkaConfiguration();
        config.setOverrides(null);
        assertNull(config.getOverrides());
    }

    // TODO(nick): Remove this test when the conversion is removed in Jan 2017.
    @Test
    public void testSetOldOverrideKey() throws Exception {
        KafkaConfiguration config = new KafkaConfiguration();
        config.setOverrides(mapWithEntry("someKafkaConfig", VALUE));
        assertEquals(VALUE, config.getOverrides().get("some.kafka.config"));
    }

    @Test
    public void testSetNewOverrideKey() throws Exception {
        KafkaConfiguration config = new KafkaConfiguration();
        config.setOverrides(mapWithEntry("some.kafka.config", VALUE));
        assertEquals(VALUE, config.getOverrides().get("some.kafka.config"));
    }

    private static Map<String, String> mapWithEntry(String key, String value) {
        Map<String, String> map = new HashMap<>();
        map.put(key, value);
        return map;
    }
}
