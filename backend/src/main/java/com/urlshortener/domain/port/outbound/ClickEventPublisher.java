package com.urlshortener.domain.port.outbound;

import com.urlshortener.domain.model.ClickEvent;
import reactor.core.publisher.Mono;

public interface ClickEventPublisher {

    Mono<Void> publish(ClickEvent event, String userAgent);
}
