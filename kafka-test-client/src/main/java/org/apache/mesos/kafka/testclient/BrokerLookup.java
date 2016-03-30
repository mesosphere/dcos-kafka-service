package org.apache.mesos.kafka.testclient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

import org.apache.mesos.kafka.testclient.ClientConfigs.BrokerLookupConfig;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BrokerLookup {
  private static final Logger LOGGER = LoggerFactory.getLogger(BrokerLookup.class);
  private static final String PATH_TEMPLATE = "service/%s/v1/connection";

  private BrokerLookupConfig config;

  public BrokerLookup(BrokerLookupConfig config) {
    this.config = config;
  }

  public List<String> getBootstrapServers() throws IOException {
    URL url;
    if (config.masterHost.startsWith("http")) {
      // assume value is of form 'http://master.mesos[/]'
      StringBuilder urlBuilder = new StringBuilder().append(config.masterHost);
      if (!config.masterHost.endsWith("/")) {
        urlBuilder.append('/');
      }
      urlBuilder.append(String.format(PATH_TEMPLATE, config.frameworkName));
      url = new URL(urlBuilder.toString());
    } else {
      // assume config is of form 'master.mesos'
      url = new URL(
          "http",
          config.masterHost,
          String.format("/" + PATH_TEMPLATE, config.frameworkName));
    }
    LOGGER.info("Connecting to {} for broker lookup", url.toString());

    if (!config.frameworkAuthToken.isEmpty()) {
      // Include provided authtoken in request headers
      URLConnection connection = url.openConnection();
      connection.setRequestProperty("Authorization", "token=" + config.frameworkAuthToken);
      connection.setDoOutput(true);
    }

    JSONObject responseObj;
    if (LOGGER.isInfoEnabled()) {
      // Intercept the response text so that it can be logged
      BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
      StringBuilder responseBuilder = new StringBuilder();
      while (true) {
        String line = reader.readLine();
        if (line == null) {
          break;
        }
        responseBuilder.append(line).append('\n');
      }
      LOGGER.info("Got response: {}", responseBuilder.toString().trim());
      responseObj = new JSONObject(responseBuilder.toString());
      LOGGER.info("JSON from response: {}", responseObj.toString());
    } else {
      // Pass response stream directly to JSONTokener, foregoing access to response content
      responseObj = new JSONObject(new JSONTokener(url.openStream()));
    }

    JSONArray responseBrokerList = responseObj.getJSONArray("brokers");
    List<String> brokerList = new ArrayList<>();
    for (int i = 0; i < responseBrokerList.length(); ++i) {
      brokerList.add(responseBrokerList.getString(i));
    }
    if (brokerList.isEmpty()) {
      LOGGER.warn("Response from {} contained an empty broker list", url.toString());
    }
    return brokerList;
  }
}
