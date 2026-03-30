package edu.kpi.fice.telegram_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableFeignClients
@EnableScheduling
public class TelegramBotServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(TelegramBotServiceApplication.class, args);
  }
}
