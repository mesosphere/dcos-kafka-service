package org.apache.mesos.kafka.testclient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ConsumerMain {

  private static final Logger LOGGER = LoggerFactory.getLogger(ConsumerMain.class);

  private static class ConsumerRunner implements Runnable {

    private final KafkaConsumer<byte[], byte[]> kafkaConsumer;
    private final ClientConfigs.ConsumerConfig consumerConfig;
    private final Stats.Values values;

    private ConsumerRunner(
        Map<String, Object> kafkaConfig,
        ClientConfigs.ConsumerConfig consumerConfig,
        Stats.Values values) {
      ByteArrayDeserializer deserializer = new ByteArrayDeserializer();
      this.kafkaConsumer = new KafkaConsumer<>(kafkaConfig, deserializer, deserializer);
      this.consumerConfig = consumerConfig;
      this.values = values;
    }

    @Override
    public void run() {
      List<String> topics = new ArrayList<>();
      topics.add(consumerConfig.topic);
      kafkaConsumer.subscribe(topics);
      while (!values.isShutdown()) {
        long messages = 0;
        long bytes = 0;
        try {
          LOGGER.info("Waiting {}ms for messages", consumerConfig.pollTimeoutMs);
          ConsumerRecords<byte[], byte[]> records = kafkaConsumer.poll(consumerConfig.pollTimeoutMs);
          LOGGER.info("Got {} messages", records.count());
          messages += records.count();
          for (ConsumerRecord<byte[], byte[]> record : records) {
            if (record.key() != null) {
              bytes += record.key().length;
            }
            bytes += record.value().length;
          }
        } catch (Throwable e) {
          values.registerError(e);
        }
        values.incMessages(messages);
        values.incBytes(bytes);
      }
      kafkaConsumer.close();
    }

  }

  public static void main(String[] args) {
    ConfigParser.Config config = ConfigParser.getConfig();
    if (config == null) {
      LOGGER.error("Unable to load base config, exiting");
      System.exit(1);
    }

    ClientConfigs.ConsumerConfig consumerConfig = config.getConsumerConfig();
    if (consumerConfig == null) {
      LOGGER.error("Unable to load consumer config, exiting");
      System.exit(1);
    }

    ClientConfigs.StatsConfig statsConfig = config.getStatsConfig();
    if (statsConfig == null) {
      LOGGER.error("Unable to load stats config, exiting");
      System.exit(1);
    }
    Stats.PrintRunner printer = new Stats.PrintRunner(statsConfig);

    ThreadRunner runner = new ThreadRunner(printer.getValues());
    runner.add("printStatsThread", printer);
    for (int i = 0; i < consumerConfig.threads; ++i) {
      ConsumerRunner consumer;
      try {
        consumer = new ConsumerRunner(config.getKafkaConfig(), consumerConfig, printer.getValues());
      } catch (Throwable e) {
        printer.getValues().registerError(e);
        System.exit(1);
        return; // happy compiler
      }
      runner.add("consumerThread-" + String.valueOf(i), consumer);
    }

    runner.runThreads();

    runner.exit();
  }
}
