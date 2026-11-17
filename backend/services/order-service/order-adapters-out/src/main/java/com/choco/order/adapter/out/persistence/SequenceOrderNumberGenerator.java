package com.choco.order.adapter.out.persistence;

import com.choco.order.application.port.OrderNumberGenerator;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Generates the human-readable order number from a database sequence.
 *
 * <p>A sequence, not a count of orders: two concurrent checkouts must not be handed the same number,
 * and deleting an order must not free its number for reuse. The sequence is the same across replicas,
 * because it lives in the shared database rather than in an instance.
 *
 * <p>The format is {@code CHOCO-<year><day-of-year>-<sequence>}, so the number itself carries the day
 * it was issued and an operator can read a support reference at a glance. The number is a convenience;
 * the order id remains the identifier everything keys on.
 */
@Component
class SequenceOrderNumberGenerator implements OrderNumberGenerator {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyDDD")
            .withZone(ZoneOffset.UTC);

    private final JdbcTemplate jdbcTemplate;

    SequenceOrderNumberGenerator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String next(Instant now) {
        Long value = jdbcTemplate.queryForObject("SELECT nextval('order_number_seq')", Long.class);
        long sequence = value == null ? 0L : value;
        return "CHOCO-" + DAY.format(now) + "-" + sequence;
    }
}
