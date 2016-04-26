package org.apache.mesos.kafka.testclient;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public final class Main {

  private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

  private static class ProducerRunner implements Runnable {

    private final KafkaProducer<byte[], byte[]> kafkaProducer;
    private final ClientConfigs.ProducerConfig producerConfig;
    private final Stats.Values values;

    private ProducerRunner(
      KafkaProducer<byte[], byte[]> kafkaProducer,
      ClientConfigs.ProducerConfig producerConfig,
      Stats.Values values) {
      this.kafkaProducer = kafkaProducer;
      this.producerConfig = producerConfig;
      this.values = values;
    }

    @Override
    public void run() {
      byte[] message = new byte[producerConfig.messageSize];
      for (int i = 0; i < message.length; ++i) {
        if (i % 2 == 0) {
          message[i] = 'x';
        } else {
          message[i] = 'o';
        }
      }
      ProducerRecord<byte[], byte[]> record = new ProducerRecord<>(producerConfig.topic, message);
      long queryPeriodMillis = producerConfig.qpsLimit > 0 ? (long) (1000. / (double) producerConfig.qpsLimit) : 0;
      while (!values.isShutdown()) {
        LOGGER.info("Sending {}-byte message...", message.length);
        Future<RecordMetadata> resultFuture = kafkaProducer.send(record);
        LOGGER.info("Sent a {}-byte message", message.length);
        if (producerConfig.synchronous) {
          try {
            resultFuture.get();
          } catch (InterruptedException | ExecutionException e) {
            values.registerError(e);
          }
        }
        if (queryPeriodMillis > 0) {
          try {
            Thread.sleep(queryPeriodMillis);
          } catch (InterruptedException e) {
            values.setFatalError(e);
            return;
          }
        }
        values.incMessages(1);
        values.incBytes(message.length);
      }
    }

  }

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
          messages = records.count();
          for (ConsumerRecord<byte[], byte[]> record : records) {
            if (record.key() != null) {
              bytes += record.key().length;
            }
            bytes += record.value().length;
          }
          LOGGER.info("Got {} messages ({} bytes)", messages, bytes);
        } catch (Throwable e) {
          values.registerError(e);
        }
        values.incMessages(messages);
        values.incBytes(bytes);
      }
      kafkaConsumer.close();
    }

  }

  private static boolean runConsumers(ConfigParser.Config config, Stats.PrintRunner printer) {
    ClientConfigs.ConsumerConfig consumerConfig = config.getConsumerConfig();
    if (consumerConfig == null) {
      LOGGER.error("Unable to load consumer config, exiting");
      return false;
    }

    ThreadRunner runner = new ThreadRunner(printer.getValues());
    runner.add("printStatsThread", printer);
    for (int i = 0; i < consumerConfig.threads; ++i) {
      ConsumerRunner consumer;
      try {
        consumer = new ConsumerRunner(config.getKafkaConfig(), consumerConfig, printer.getValues());
      } catch (Throwable e) {
        printer.getValues().registerError(e);
        return false;
      }
      runner.add("consumerThread-" + String.valueOf(i), consumer);
    }

    runner.runThreads();
    return !runner.isFatalError();
  }

  private static boolean runProducers(ConfigParser.Config config, Stats.PrintRunner printer) {
    ClientConfigs.ProducerConfig producerConfig = config.getProducerConfig();
    if (producerConfig == null) {
      LOGGER.error("Unable to load producer config, exiting");
      return false;
    }

    // Share a single KafkaProducer instance across all threads
    ByteArraySerializer serializer = new ByteArraySerializer();
    KafkaProducer<byte[], byte[]> kafkaProducer;
    try {
      kafkaProducer = new KafkaProducer<>(config.getKafkaConfig(), serializer, serializer);
    } catch (Throwable e) {
      printer.getValues().registerError(e);
      return false; // happy compiler
    }

    ThreadRunner runner = new ThreadRunner(printer.getValues());
    runner.add("printStatsThread", printer);
    for (int i = 0; i < producerConfig.threads; ++i) {
      ProducerRunner producer = new ProducerRunner(kafkaProducer, producerConfig, printer.getValues());
      runner.add("producerThread-" + String.valueOf(i), producer);
    }

    runner.runThreads();
    kafkaProducer.close();
    return !runner.isFatalError();
  }

  public static void main(String[] args) {
    ConfigParser.Config config = ConfigParser.getConfig();
    if (config == null) {
      LOGGER.error("Unable to load base config, exiting");
      System.exit(1);
    }

    ClientConfigs.StatsConfig statsConfig = config.getStatsConfig();
    if (statsConfig == null) {
      LOGGER.error("Unable to load stats config, exiting");
      System.exit(1);
    }
    Stats.PrintRunner printer = new Stats.PrintRunner(statsConfig);

    boolean success = true;
    switch (config.getClientMode()) {

    case CONSUMER:
      success = runConsumers(config, printer);
      break;

    case PRODUCER:
      success = runProducers(config, printer);
      break;

    case NONE:
    default:
      LOGGER.error("Invalid client mode: " + config.getClientMode());
      success = false;
      break;
    }

    System.exit(success ? 0 : 1);
  }
}
