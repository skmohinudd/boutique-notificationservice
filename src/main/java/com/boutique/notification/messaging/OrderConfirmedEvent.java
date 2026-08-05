package com.boutique.notification.messaging;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
public record OrderConfirmedEvent(UUID orderId,UUID userId,UUID paymentId,BigDecimal total,String currency,Instant occurredAt){}
