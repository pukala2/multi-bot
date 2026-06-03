package com.multibot.billing.application;

import com.multibot.billing.domain.model.AiCreditSummary;
import com.multibot.billing.domain.model.AiCreditSummary.ProviderWallet;
import com.multibot.billing.domain.model.AiProvider;
import com.multibot.billing.domain.port.in.ManageAiCreditUseCase;
import com.multibot.billing.domain.port.out.AiCreditRepository;
import com.multibot.billing.domain.port.out.TenantUsageRepository;
import com.multibot.shared.util.UuidGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Portfel AI per dostawca (poziom platformy, tylko super admin).
 *   OPENAI: spent = embedding_cost (indeksacja).  ANTHROPIC: spent = cost_usd (czat).
 *   remaining = toppedUp − spent. spent to NASZ szacunek, nie faktura dostawcy.
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
        ProviderWallet openai = wallet(AiProvider.OPENAI, usageRepository.sumAllEmbeddingCost());
        ProviderWallet anthropic = wallet(AiProvider.ANTHROPIC, usageRepository.sumAllChatCost());

        BigDecimal totalToppedUp = openai.toppedUp().add(anthropic.toppedUp());
        BigDecimal totalSpent = openai.spent().add(anthropic.spent());
        return new AiCreditSummary(
                List.of(openai, anthropic),
                totalToppedUp, totalSpent, totalToppedUp.subtract(totalSpent),
                creditRepository.listTopups());
    }

    private ProviderWallet wallet(AiProvider provider, BigDecimal spent) {
        BigDecimal toppedUp = creditRepository.sumTopups(provider);
        return new ProviderWallet(provider, toppedUp, spent, toppedUp.subtract(spent));
    }

    @Override
    @Transactional
    public AiCreditSummary addTopup(AiProvider provider, BigDecimal amountUsd, String note, UUID createdBy) {
        creditRepository.insertTopup(
                UuidGenerator.newUuidV7(), provider, amountUsd,
                (note == null || note.isBlank()) ? null : note.trim(),
                createdBy, Instant.now());
        return summary();
    }
}
