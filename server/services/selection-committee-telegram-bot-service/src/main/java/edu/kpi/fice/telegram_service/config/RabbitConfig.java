package edu.kpi.fice.telegram_service.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

  public static final String AUDIT_EXCHANGE = "audit.events";
  public static final String BOT_NOTIFICATIONS_QUEUE = "telegram.bot.flag-notifications";
  public static final String BOT_NOTIFICATIONS_DLQ = "telegram.bot.flag-notifications.dlq";
  public static final String ROUTING_KEY = "audit.ingest";

  @Bean
  public TopicExchange auditExchange() {
    return new TopicExchange(AUDIT_EXCHANGE);
  }

  @Bean
  public Queue botNotificationsQueue() {
    return QueueBuilder.durable(BOT_NOTIFICATIONS_QUEUE)
        .deadLetterExchange("")
        .deadLetterRoutingKey(BOT_NOTIFICATIONS_DLQ)
        .build();
  }

  @Bean
  public Queue botNotificationsDlq() {
    return QueueBuilder.durable(BOT_NOTIFICATIONS_DLQ).build();
  }

  @Bean
  public Binding botNotificationsBinding(Queue botNotificationsQueue, TopicExchange auditExchange) {
    return BindingBuilder.bind(botNotificationsQueue).to(auditExchange).with("audit.ingest");
  }

  @Bean
  public MessageConverter jsonMessageConverter() {
    return new Jackson2JsonMessageConverter();
  }
}
