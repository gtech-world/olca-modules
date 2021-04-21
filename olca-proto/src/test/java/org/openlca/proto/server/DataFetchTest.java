package org.openlca.proto.server;

import static org.junit.Assert.*;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openlca.core.database.IDatabase;
import org.openlca.core.model.Actor;
import org.openlca.proto.Tests;
import org.openlca.proto.generated.Proto;
import org.openlca.proto.generated.data.DataFetchServiceGrpc;
import org.openlca.proto.generated.data.GetDescriptorsRequest;

public class DataFetchTest {

  private final IDatabase db = Tests.db();

  @Test
  public void getDescriptors() {

    // create some actors
    var actors = new Actor[10];
    var found = new boolean[actors.length];
    for (int i = 0; i < actors.length; i++) {
      actors[i] = db.insert(Actor.of("actor " + i));
    }

    // collect the descriptors from the service
    ServiceTests.on(channel -> {
      var stub = DataFetchServiceGrpc.newBlockingStub(channel);
      var refs = stub.getDescriptors(
        GetDescriptorsRequest.newBuilder()
          .setModelType(Proto.ModelType.ACTOR)
          .build());
      while (refs.hasNext()) {
        var ref = refs.next();
        for (int i = 0; i < actors.length; i++) {
          if (ref.getId().equals(actors[i].refId)) {
            found[i] = true;
          }
        }
      }
    });

    // delete them
    for (int i = 0; i < actors.length; i++) {
      assertTrue(found[i]);
      db.delete(actors[i]);
    }
  }
}