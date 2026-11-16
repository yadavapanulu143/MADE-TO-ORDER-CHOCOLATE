package com.choco.events.config;

/**
 * RabbitMQ topology: one durable topic exchange, per-event-type queues with dead-lettering.
 *
 * <p>A topic exchange (rather than fanout) lets a consumer subscribe to a family of events such as
 * {@code payment.*} while another subscribes to one exact key. Durable queues plus persistent
 * messages mean a broker restart does not drop events, and the dead-letter queue keeps a message
 * that repeatedly fails visible instead of silently discarding it.
 */
public final class RabbitTopology {

    public static final String EXCHANGE = "choco.events";
    public static final String DEAD_LETTER_EXCHANGE = "choco.events.dlx";
    public static final String DEAD_LETTER_QUEUE = "choco.events.dlq";
    public static final String DEAD_LETTER_ROUTING_KEY = "dead-letter";

    /** Retry queue with a TTL; messages expire back onto the main exchange after a delay. */
    public static final String RETRY_EXCHANGE = "choco.events.retry";
    public static final String RETRY_QUEUE_SUFFIX = ".retry";
    public static final String RETRY_ROUTING_KEY_PREFIX = "retry.";

    private RabbitTopology() {
    }

    public static String queueName(String consumerGroup) {
        return "choco." + consumerGroup;
    }

    public static String retryQueueName(String consumerGroup) {
        return queueName(consumerGroup) + RETRY_QUEUE_SUFFIX;
    }
}
