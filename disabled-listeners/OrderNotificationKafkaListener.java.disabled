package com.boutique.notification.service;

import com.boutique.notification.event.OrderConfirmedEvent;
import tools.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OrderNotificationKafkaListener {

    private static final Logger log =
            LoggerFactory.getLogger(
                    OrderNotificationKafkaListener.class
            );

    // Local phase only. The AWS production version will
    // persist processed event IDs in PostgreSQL/DynamoDB.
    private final Set<UUID> processedEventIds =
            ConcurrentHashMap.newKeySet();

    private final ObjectMapper objectMapper;

    public OrderNotificationKafkaListener(
            ObjectMapper objectMapper
    ) {
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics =
                    "${kafka.topics.order-events:"
                    + "boutique.order.events}",
            groupId =
                    "${spring.kafka.consumer.group-id:"
                    + "notification-service}"
    )
    public void handle(
            ConsumerRecord<String, String> record
    ) throws Exception {
        OrderConfirmedEvent event =
                objectMapper.readValue(
                        record.value(),
                        OrderConfirmedEvent.class
                );

        if (!processedEventIds.add(event.eventId())) {
            log.info(
                    "Skipping duplicate eventId={}",
                    event.eventId()
            );
            return;
        }

        log.info(
                "Order confirmation notification accepted "
                        + "eventId={} orderId={} userId={} "
                        + "paymentId={} total={} {} "
                        + "partition={} offset={}",
                event.eventId(),
                event.data().orderId(),
                event.data().userId(),
                event.data().paymentId(),
                event.data().total(),
                event.data().currency(),
                record.partition(),
                record.offset()
        );
    }
}
