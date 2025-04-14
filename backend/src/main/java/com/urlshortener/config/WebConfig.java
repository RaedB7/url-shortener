package com.urlshortener.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.urlshortener.domain.port.inbound.AuthUseCase;
import com.urlshortener.infrastructure.web.CurrentUser;

@Configuration
public class WebConfig {

    @Bean
    public CurrentUser.UserLookup userLookup(AuthUseCase authUseCase) {
        return authUseCase::byId;
    }
}
