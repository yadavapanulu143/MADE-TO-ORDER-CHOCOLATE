package com.choco.order.adapter.out.persistence;

import com.choco.order.application.port.InvoiceNumberGenerator;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Generates the human-readable invoice number from a database sequence.
 *
 * <p>A sequence, not a count of invoices: two concurrent issuances must not be handed the same number,
 * and deleting an invoice must not free its number for reuse. An invoice number is a
 * financial-document reference, so it must be unique on its own and never reused.
 *
 * <p>The format is {@code INV-<year><day-of-year>-<sequence>}, so the number carries the day it was
 * issued. The invoice id remains the identifier the API keys on.
 */
@Component
class SequenceInvoiceNumberGenerator implements InvoiceNumberGenerator {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyDDD")
            .withZone(ZoneOffset.UTC);

    private final JdbcTemplate jdbcTemplate;

    SequenceInvoiceNumberGenerator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String next(Instant now) {
        Long value = jdbcTemplate.queryForObject("SELECT nextval('invoice_number_seq')", Long.class);
        long sequence = value == null ? 0L : value;
        return "INV-" + DAY.format(now) + "-" + sequence;
    }
}
