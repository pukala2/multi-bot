package com.multibot.billing.application;

import com.multibot.billing.domain.model.AiCreditSummary;
import com.multibot.billing.domain.port.in.ManageAiCreditUseCase;
import com.multibot.billing.domain.port.out.AiCreditRepository;
import com.multibot.billing.domain.port.out.TenantUsageRepository;
import com.multibot.shared.util.UuidGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Portfel AI (poziom platformy, tylko super admin):
 *   pozostało = SUMA(doładowania) − SUMA(cost_usd wszystkich botów, all-time).
 * cost_usd to nasz szacunek, nie faktura dostawcy.
 */
@Service
class AiCreditService implements ManageAiCreditUseCase {

    private final AiCreditRepository creditRepository;
    private final TenantUsageRepository usageRepository;

    AiCreditService(AiCreditRepository creditRepository, TenantUsageRepository usageRepository) {
        this.creditRepository = creditRepository;
        this.usageRepository = usageRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public AiCreditSummary summary() {
        BigDecimal toppedUp = creditRepository.sumTopups();
        BigDecimal spent = usageRepository.sumAllCost();
        return new AiCreditSummary(toppedUp, spent, toppedUp.subtract(spent), creditRepository.listTopups());
    }

    @Override
    @Transactional
    public AiCreditSummary addTopup(BigDecimal amountUsd, String note, UUID createdBy) {
        creditRepository.insertTopup(
                UuidGenerator.newUuidV7(), amountUsd,
                (note == null || note.isBlank()) ? null : note.trim(),
                createdBy, Instant.now());
        return summary();
    }
}
