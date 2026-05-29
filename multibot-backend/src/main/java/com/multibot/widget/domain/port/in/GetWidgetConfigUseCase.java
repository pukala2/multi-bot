package com.multibot.widget.domain.port.in;

import com.multibot.shared.tenant.TenantId;
import com.multibot.widget.domain.model.WidgetConfig;
import com.multibot.widget.domain.model.WidgetSettings;

import java.util.List;
import java.util.Optional;

public interface GetWidgetConfigUseCase {

    WidgetConfig getForCurrentTenant();

    Optional<WidgetSettings> getByPublicKey(String key);

    /**
     * Sprawdza czy dany Origin moze uzywac danego klucza.
     * Pusta lista allowedOrigins = klucz dziala z kazdej domeny (dev/test).
     * Niepusta lista = origin musi byc na liscie.
     */
    boolean isOriginAllowed(String key, String origin);

    /** Lista dozwolonych originów dla danego klucza (do zarzadzania w panelu). */
    List<String> getAllowedOrigins(String key);

    /** Dozwolone origins aktywnego bota (z TenantContext) — do edycji w panelu Widget. */
    List<String> getAllowedOriginsForCurrentTenant();

    /**
     * Walidacja klucza widgeta dla zadania anonimowego (chat).
     * Zwraca tenant jezeli: klucz aktywny ORAZ origin dozwolony.
     * Inaczej empty (= 403 po stronie filtra).
     */
    Optional<TenantId> validateAndResolveTenant(String key, String origin);
}
