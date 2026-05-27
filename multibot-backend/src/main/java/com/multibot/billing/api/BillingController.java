package com.multibot.billing.api;

import com.multibot.billing.domain.model.BillingPlan;
import com.multibot.billing.domain.model.TenantUsage;
import com.multibot.billing.domain.port.in.GetUsageUseCase;
import com.multibot.shared.tenant.TenantId;
import com.multibot.tenancy.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

/**
 * Endpointy dla zwykłego admina (klienta panelu).
 * Zwracaja TYLKO user-friendly liczby — bez tokenow i kosztow OpenAI.
 * Te ostatnie idą do super admin endpointow (planowane w innej fazie).
 */
@RestController
@RequestMapping("/api/admin/billing")
class BillingController {

    private final GetUsageUseCase getUsageUseCase;

    BillingController(GetUsageUseCase getUsageUseCase) {
        this.getUsageUseCase = getUsageUseCase;
    }

    @GetMapping("/usage")
    ResponseEntity<Map<String, Object>> getUsage() {
        TenantId tenant = TenantContext.get();
        BillingPlan plan = getUsageUseCase.getPlan(tenant);
        TenantUsage usage = getUsageUseCase.getCurrentUsage(tenant);
        int effectiveLimit = getUsageUseCase.getEffectiveLimit(tenant);

        LocalDate today = LocalDate.now();
        LocalDate nextReset = YearMonth.now().plusMonths(1).atDay(1);
        long daysToReset = ChronoUnit.DAYS.between(today, nextReset);

        double percent = effectiveLimit == -1 ? 0.0
                : (effectiveLimit == 0 ? 0.0 : (usage.userMessages() * 100.0) / effectiveLimit);

        Map<String, Object> body = new HashMap<>();
        body.put("plan", plan.code());
        body.put("planName", plan.name());
        body.put("monthlyMessagesLimit", plan.monthlyMessagesLimit());
        body.put("effectiveLimit", effectiveLimit);
        body.put("userMessages", usage.userMessages());
        body.put("aiMessages", usage.aiMessages());
        body.put("periodMonth", usage.periodMonth().toString());
        body.put("resetsAt", nextReset.atStartOfDay().toString() + "Z");
        body.put("remainingDays", daysToReset);
        body.put("percentUsed", Math.round(percent * 10) / 10.0);
        body.put("unlimited", effectiveLimit == -1);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/plan")
    ResponseEntity<Map<String, Object>> getPlan() {
        TenantId tenant = TenantContext.get();
        BillingPlan plan = getUsageUseCase.getPlan(tenant);
        int effectiveLimit = getUsageUseCase.getEffectiveLimit(tenant);

        Map<String, Object> body = new HashMap<>();
        body.put("code", plan.code());
        body.put("name", plan.name());
        body.put("monthlyMessagesLimit", plan.monthlyMessagesLimit());
        body.put("effectiveLimit", effectiveLimit);
        body.put("customDomainAllowed", plan.customDomainAllowed());
        body.put("apiAccessAllowed", plan.apiAccessAllowed());
        body.put("pricePlnMonthly", plan.pricePlnMonthly());
        return ResponseEntity.ok(body);
    }
}
