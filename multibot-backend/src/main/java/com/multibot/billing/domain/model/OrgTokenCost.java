package com.multibot.billing.domain.model;

import java.math.BigDecimal;

/** Zużycie tokenów i koszt organizacji (suma jej botów) w danym okresie. */
public record OrgTokenCost(long tokensUsed, BigDecimal costUsd) {}
