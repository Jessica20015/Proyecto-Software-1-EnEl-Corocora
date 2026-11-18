package com.enel.portal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@SpringBootApplication(scanBasePackages = "com.enel")
@EntityScan("com.enel.shared")
@EnableJpaRepositories("com.enel.shared")
public class PortalApplication {
    @Bean
    RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder.connectTimeout(Duration.ofSeconds(3)).readTimeout(Duration.ofSeconds(15)).build();
    }

    public static void main(String[] args) {
        SpringApplication.run(PortalApplication.class, args);
    }
}
