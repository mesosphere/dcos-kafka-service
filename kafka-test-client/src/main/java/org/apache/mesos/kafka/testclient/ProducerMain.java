package org.apache.mesos.kafka.testclient;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public final class ProducerMain {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProducerMain.class);

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

  public static void main(String[] args) {
    ConfigParser.Config config = ConfigParser.getConfig();
    if (config == null) {
      LOGGER.error("Unable to load base config, exiting");
      System.exit(1);
    }

    ClientConfigs.ProducerConfig producerConfig = config.getProducerConfig();
    if (producerConfig == null) {
      LOGGER.error("Unable to load producer config, exiting");
      System.exit(1);
    }

    ClientConfigs.StatsConfig statsConfig = config.getStatsConfig();
    if (statsConfig == null) {
      LOGGER.error("Unable to load stats config, exiting");
      System.exit(1);
    }
    Stats.PrintRunner printer = new Stats.PrintRunner(statsConfig);

    // Share a single KafkaProducer instance across all threads
    ByteArraySerializer serializer = new ByteArraySerializer();
    KafkaProducer<byte[], byte[]> kafkaProducer;
    try {
      kafkaProducer = new KafkaProducer<>(config.getKafkaConfig(), serializer, serializer);
    } catch (Throwable e) {
      printer.getValues().registerError(e);
      System.exit(1);
      return; // happy compiler
    }

    ThreadRunner runner = new ThreadRunner(printer.getValues());
    runner.add("printStatsThread", printer);
    for (int i = 0; i < producerConfig.threads; ++i) {
      ProducerRunner producer = new ProducerRunner(kafkaProducer, producerConfig, printer.getValues());
      runner.add("producerThread-" + String.valueOf(i), producer);
    }

    runner.runThreads();

    kafkaProducer.close();
    runner.exit();
  }
}
