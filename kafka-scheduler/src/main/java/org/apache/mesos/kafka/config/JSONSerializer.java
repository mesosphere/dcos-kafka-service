package org.apache.mesos.kafka.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

import java.io.IOException;

public class JSONSerializer {
    public static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new GuavaModule())
            .registerModule(new Jdk8Module());

    public static String toJsonString(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "";
        }
    }

    public static byte[] toBytes(Object value) throws IOException {
        try {
            return MAPPER.writeValueAsBytes(value);
        } catch (JsonProcessingException e) {
            throw new IOException("Error writing: " + value + " to bytes");
        }
    }

    public static <T> T fromBytes(byte[] bytes, Class<T> clazz) throws IOException {
        return MAPPER.readValue(bytes, clazz);
    }
}
