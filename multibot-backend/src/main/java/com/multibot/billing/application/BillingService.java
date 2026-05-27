package com.multibot.billing.application;

import com.multibot.billing.domain.model.BillingPlan;
import com.multibot.billing.domain.model.MessageLimitDecision;
import com.multibot.billing.domain.model.TenantPlanInfo;
import com.multibot.billing.domain.model.TenantUsage;
import com.multibot.billing.domain.port.in.CheckMessageLimitUseCase;
import com.multibot.billing.domain.port.in.GetUsageUseCase;
import com.multibot.billing.domain.port.in.IncrementUsageUseCase;
import com.multibot.billing.domain.port.out.BillingPlanRepository;
import com.multibot.billing.domain.port.out.TenantUsageRepository;
import com.multibot.shared.tenant.TenantId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

@Service
class BillingService implements CheckMessageLimitUseCase, IncrementUsageUseCase, GetUsageUseCase {

    private final BillingPlanRepository planRepository;
    private final TenantUsageRepository usageRepository;

    BillingService(BillingPlanRepository planRepository,
                   TenantUsageRepository usageRepository) {
        this.planRepository = planRepository;
        this.usageRepository = usageRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public MessageLimitDecision check(TenantId tenantId) {
        TenantPlanInfo info = requirePlanInfo(tenantId);
        LocalDate period = currentPeriodMonth();
        TenantUsage usage = usageRepository.findCurrent(tenantId, period);

        int effective = info.effectiveLimit();
        long used = usage.userMessages();
        boolean allowed = effective == -1 || used < effective;

        return new MessageLimitDecision(allowed, effective, used, nextPeriodMonth(period));
    }

    @Override
    @Transactional
    public long incrementUserMessage(TenantId tenantId) {
        return usageRepository.incrementUserMessages(tenantId, currentPeriodMonth());
    }

    @Override
    @Transactional
    public void incrementAiMessage(TenantId tenantId, long tokensUsed, BigDecimal costUsd) {
        usageRepository.incrementAiMessages(tenantId, currentPeriodMonth(), tokensUsed, costUsd);
    }

    @Override
    @Transactional(readOnly = true)
    public TenantUsage getCurrentUsage(TenantId tenantId) {
        return usageRepository.findCurrent(tenantId, currentPeriodMonth());
    }

    @Override
    @Transactional(readOnly = true)
    public BillingPlan getPlan(TenantId tenantId) {
        return requirePlanInfo(tenantId).plan();
    }

    @Override
    @Transactional(readOnly = true)
    public int getEffectiveLimit(TenantId tenantId) {
        return requirePlanInfo(tenantId).effectiveLimit();
    }

    private TenantPlanInfo requirePlanInfo(TenantId tenantId) {
        return planRepository.findTenantPlanInfo(tenantId)
                .orElseThrow(() -> new IllegalStateException("Tenant " + tenantId.value() + " has no plan assigned"));
    }

    private static LocalDate currentPeriodMonth() {
        return YearMonth.now().atDay(1);
    }

    private static LocalDate nextPeriodMonth(LocalDate current) {
        return current.plus(1, ChronoUnit.MONTHS);
    }
}
