package com.boutique.notification.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderConfirmedEvent(
        UUID eventId,
        String eventType,
        int eventVersion,
        UUID aggregateId,
        Instant occurredAt,
        Data data
) {
    public record Data(
            UUID orderId,
            UUID userId,
            UUID paymentId,
            BigDecimal total,
            String currency
    ) {
    }
}
