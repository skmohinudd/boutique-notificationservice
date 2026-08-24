package com.boutique.notification.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class DomainEventNotificationListener {
    private static final Logger log = LoggerFactory.getLogger(DomainEventNotificationListener.class);

    private final ObjectMapper json;
    private final JavaMailSender mail;
    private final StringRedisTemplate redis;
    private final String from;
    private final String fallbackRecipient;
    private final String userServiceUrl;
    private final Duration dedupeTtl;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    public DomainEventNotificationListener(
            ObjectMapper json,
            JavaMailSender mail,
            StringRedisTemplate redis,
            @Value("${notification.email.from:no-reply@boutique.local}") String from,
            @Value("${notification.email.default-recipient:demo.customer@boutique.local}") String fallbackRecipient,
            @Value("${notification.user-service-url:http://userservice:8082}") String userServiceUrl,
            @Value("${notification.dedupe-ttl:7d}") Duration dedupeTtl) {
        this.json = json;
        this.mail = mail;
        this.redis = redis;
        this.from = from;
        this.fallbackRecipient = fallbackRecipient;
        this.userServiceUrl = userServiceUrl.replaceAll("/+$", "");
        this.dedupeTtl = dedupeTtl;
    }

    @KafkaListener(
            topics = {
                    "${app.kafka.order-events-topic}",
                    "${app.kafka.payment-events-topic}",
                    "${app.kafka.shipping-events-topic}"
            },
            groupId = "${spring.kafka.consumer.group-id}")
    public void kafka(String payload) throws Exception {
        handle(payload, "kafka");
    }

    @RabbitListener(queues = "boutique.notification.events")
    public void rabbit(String payload) throws Exception {
        handle(payload, "rabbitmq");
    }

    private void handle(String payload, String transport) throws Exception {
        var event = json.readTree(payload);
        String type = event.path("eventType").asText();

        // Order, payment and shipping events share the transports. Only the
        // confirmed order sends the customer confirmation email.
        if (!"ORDER_CONFIRMED".equals(type)) {
            log.info("NOTIFICATION_EVENT_OBSERVED eventType={} transport={}", type, transport);
            return;
        }

        String eventId = event.path("eventId").asText();
        if (eventId.isBlank()) {
            throw new IllegalArgumentException("Notification event is missing eventId");
        }

        // The Order Outbox can publish to both Kafka and RabbitMQ. Redis makes
        // notification delivery idempotent across both transports.
        String dedupeKey = "notification:processed:" + eventId;
        Boolean claimed = redis.opsForValue().setIfAbsent(dedupeKey, "1", dedupeTtl);
        if (!Boolean.TRUE.equals(claimed)) {
            log.info("NOTIFICATION_DUPLICATE_SKIPPED transport={} eventId={}", transport, eventId);
            return;
        }

        try {
            String orderId = event.path("orderId").asText("n/a");
            String userId = event.path("userId").asText("");
            String recipient = resolveRecipient(userId);

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(recipient);
            message.setSubject("Boutique order confirmed — " + orderId);
            message.setText("""
                    Your Boutique order is confirmed.

                    Order ID: %s
                    Status: %s
                    Event transport: %s

                    Thank you for shopping with Boutique.
                    This DEV environment captures email in Mailpit.
                    """.formatted(
                    orderId,
                    event.path("status").asText("CONFIRMED"),
                    transport));

            mail.send(message);
            log.info(
                    "ORDER_CONFIRMATION_EMAIL_SENT orderId={} recipient={} eventId={}",
                    orderId, recipient, eventId);
        } catch (Exception failure) {
            // Allow a retry if the downstream user lookup or mail delivery fails.
            redis.delete(dedupeKey);
            throw failure;
        }
    }

    private String resolveRecipient(String userId) {
        if (userId == null || userId.isBlank()) return fallbackRecipient;

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(userServiceUrl + "/api/v1/users/" + userId))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();

            HttpResponse<String> response = http.send(
                    request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                String email = json.readTree(response.body()).path("email").asText("");
                if (!email.isBlank()) return email;
            }

            log.warn("USER_EMAIL_LOOKUP_FAILED userId={} status={}", userId, response.statusCode());
        } catch (Exception error) {
            log.warn("USER_EMAIL_LOOKUP_FAILED userId={} reason={}", userId, error.toString());
        }

        return fallbackRecipient;
    }
}
