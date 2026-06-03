package com.multibot.billing.domain.port.out;

import com.multibot.billing.domain.model.OrgTokenCost;
import com.multibot.billing.domain.model.TenantUsage;
import com.multibot.shared.tenant.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface TenantUsageRepository {

    /** Zwraca biezacy wiersz albo empty (gdy jeszcze nie ma rekordu na ten miesiac). */
    TenantUsage findCurrent(TenantId tenantId, LocalDate periodMonth);

    /**
     * Suma user_messages WSZYSTKICH botow organizacji do ktorej nalezy podany bot, w danym miesiacu.
     * Limit jest wspolny (pooled) na cala organizacje, wiec to ta liczba liczy sie do limitu.
     */
    long sumUserMessagesForOrgOf(TenantId tenantId, LocalDate periodMonth);

    /**
     * UPSERT + atomowy +1 do user_messages. Zwraca nowa wartosc countera.
     * Race-safe nawet przy rownoleglych zapytaniach.
     */
    long incrementUserMessages(TenantId tenantId, LocalDate periodMonth);

    /**
     * UPSERT + atomowy +1 do ai_messages, +N tokens_used, +N.NNNN cost_usd.
     */
    void incrementAiMessages(TenantId tenantId, LocalDate periodMonth, long tokensUsed, BigDecimal costUsd);

    /** Suma cost_usd wszystkich botow wszystkich organizacji od poczatku (all-time) — wydane z portfela AI. */
    BigDecimal sumAllCost();

    /** [tokens_used, cost_usd] sumarycznie dla botow organizacji (po anyBot) w danym miesiacu. */
    OrgTokenCost sumTokensCostForOrgOf(TenantId tenantId, LocalDate periodMonth);
}
