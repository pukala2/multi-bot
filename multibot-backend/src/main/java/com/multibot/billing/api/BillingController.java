package com.multibot.billing.api;

import com.multibot.api.generated.api.BillingApi;
import com.multibot.api.generated.model.BillingPlanResponse;
import com.multibot.api.generated.model.BillingUsageResponse;
import com.multibot.billing.domain.model.BillingPlan;
import com.multibot.billing.domain.model.TenantUsage;
import com.multibot.billing.domain.port.in.GetUsageUseCase;
import com.multibot.shared.tenant.TenantId;
import com.multibot.tenancy.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

/**
 * Endpointy dla zwykłego admina (klienta panelu) — contract-first, implementuje {@link BillingApi}.
 * Zwracaja TYLKO user-friendly liczby — bez tokenow i kosztow OpenAI (te idą do super admina).
 */
@RestController
class BillingController implements BillingApi {

    private final GetUsageUseCase getUsageUseCase;

    BillingController(GetUsageUseCase getUsageUseCase) {
        this.getUsageUseCase = getUsageUseCase;
    }

    @Override
    public ResponseEntity<BillingUsageResponse> getBillingUsage() {
        TenantId tenant = TenantContext.get();
        BillingPlan plan = getUsageUseCase.getPlan(tenant);
        TenantUsage usage = getUsageUseCase.getCurrentUsage(tenant);
        // Limit jest wspolny dla calej organizacji -> pokazujemy sume wiadomosci wszystkich botow.
        long usedMessages = getUsageUseCase.getPooledUsedMessages(tenant);
        int effectiveLimit = getUsageUseCase.getEffectiveLimit(tenant);

        LocalDate today = LocalDate.now();
        LocalDate nextReset = YearMonth.now().plusMonths(1).atDay(1);
        long daysToReset = ChronoUnit.DAYS.between(today, nextReset);

        double percent = effectiveLimit == -1 ? 0.0
                : (effectiveLimit == 0 ? 0.0 : (usedMessages * 100.0) / effectiveLimit);

        BillingUsageResponse body = new BillingUsageResponse(
                plan.code(),
                plan.name(),
                plan.monthlyMessagesLimit(),
                effectiveLimit,
                usedMessages,
                usage.aiMessages(),
                usage.periodMonth().toString(),
                nextReset.atStartOfDay().toString() + "Z",
                daysToReset,
                Math.round(percent * 10) / 10.0,
                effectiveLimit == -1);
        return ResponseEntity.ok(body);
    }

    @Override
    public ResponseEntity<BillingPlanResponse> getBillingPlan() {
        TenantId tenant = TenantContext.get();
        BillingPlan plan = getUsageUseCase.getPlan(tenant);
        int effectiveLimit = getUsageUseCase.getEffectiveLimit(tenant);

        BillingPlanResponse body = new BillingPlanResponse(
                plan.code(),
                plan.name(),
                plan.monthlyMessagesLimit(),
                effectiveLimit,
                plan.customDomainAllowed(),
                plan.apiAccessAllowed());
        body.setMaxBots(plan.maxBots());                 // null = bez limitu
        body.setPricePlnMonthly(plan.pricePlnMonthly()); // null = custom pricing
        return ResponseEntity.ok(body);
    }
}
