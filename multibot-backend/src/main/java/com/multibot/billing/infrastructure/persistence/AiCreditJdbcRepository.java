package com.multibot.billing.infrastructure.persistence;

import com.multibot.billing.domain.model.AiCreditTopup;
import com.multibot.billing.domain.model.AiProvider;
import com.multibot.billing.domain.port.out.AiCreditRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
class AiCreditJdbcRepository implements AiCreditRepository {

    private final JdbcTemplate jdbc;

    AiCreditJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insertTopup(UUID id, AiProvider provider, BigDecimal amountUsd, String note, UUID createdBy, Instant createdAt) {
        jdbc.update(
                "INSERT INTO ai_credit_topups (id, provider, amount_usd, note, created_by, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                id, provider.name(), amountUsd, note, createdBy, Timestamp.from(createdAt));
    }

    @Override
    public BigDecimal sumTopups(AiProvider provider) {
        BigDecimal sum = jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount_usd), 0) FROM ai_credit_topups WHERE provider = ?",
                BigDecimal.class, provider.name());
        return sum == null ? BigDecimal.ZERO : sum;
    }

    @Override
    public List<AiCreditTopup> listTopups() {
        return jdbc.query(
                "SELECT id, provider, amount_usd, note, created_at FROM ai_credit_topups ORDER BY created_at DESC",
                (rs, rowNum) -> new AiCreditTopup(
                        rs.getObject("id", UUID.class),
                        AiProvider.valueOf(rs.getString("provider")),
                        rs.getBigDecimal("amount_usd"),
                        rs.getString("note"),
                        rs.getTimestamp("created_at").toInstant()));
    }
}
