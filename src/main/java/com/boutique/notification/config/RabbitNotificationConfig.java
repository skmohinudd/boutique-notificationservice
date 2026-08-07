package com.boutique.notification.config;
import org.springframework.amqp.core.*;import org.springframework.context.annotation.*;
@Configuration public class RabbitNotificationConfig{
 @Bean TopicExchange boutiqueEvents(){return new TopicExchange("boutique.events",true,false);}
 @Bean TopicExchange boutiqueNotificationDlx(){return new TopicExchange("boutique.dlx",true,false);}
 @Bean Queue notificationQueue(){return QueueBuilder.durable("boutique.notification.events").deadLetterExchange("boutique.dlx").deadLetterRoutingKey("notification.failed").build();}
 @Bean Queue notificationDlq(){return QueueBuilder.durable("boutique.notification.events.dlq").build();}
 @Bean Binding notificationBinding(Queue notificationQueue,TopicExchange boutiqueEvents){return BindingBuilder.bind(notificationQueue).to(boutiqueEvents).with("#");}
 @Bean Binding notificationDlqBinding(Queue notificationDlq,TopicExchange boutiqueNotificationDlx){return BindingBuilder.bind(notificationDlq).to(boutiqueNotificationDlx).with("notification.failed");}
}
