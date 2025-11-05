package com.tbelousov.tutube.event;

public interface EventHandler<E extends DomainEvent> {
    Class<E> eventType();
    void handle(E event) throws Exception;
}