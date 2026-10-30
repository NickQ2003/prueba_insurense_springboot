package com.example.policies.application.port.out;

import com.example.policies.application.usecase.OutboxEvent;
import java.util.List;

public interface OutboxRepository {

  List<OutboxEvent> claimReadyBatch(int limit, String leaseOwner);

  boolean markPublished(OutboxEvent event, String leaseOwner);

  void releaseForRetry(OutboxEvent event, String leaseOwner, int delaySeconds);
}
