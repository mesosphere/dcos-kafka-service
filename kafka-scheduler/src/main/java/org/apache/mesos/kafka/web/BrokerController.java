package org.apache.mesos.kafka.web;

import com.fasterxml.jackson.core.JsonFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.MediaType;

@Path("/")
@Produces("application/json")
public class BrokerController {
  private final JsonFactory factory = new JsonFactory();

  @GET
  public Response broker() {
    return Response.ok("Foo bar baz", MediaType.APPLICATION_JSON).build();
  }
}
