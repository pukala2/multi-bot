package com.multibot.rag.settings.api;

import com.multibot.api.generated.api.RagSettingsApi;
import com.multibot.api.generated.model.RagSettingsRequest;
import com.multibot.api.generated.model.RagSettingsResponse;
import com.multibot.rag.settings.domain.model.RagSettings;
import com.multibot.rag.settings.domain.port.in.GetRagSettingsUseCase;
import com.multibot.rag.settings.domain.port.in.UpdateRagSettingsUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
class RagSettingsController implements RagSettingsApi {

    private final GetRagSettingsUseCase getUseCase;
    private final UpdateRagSettingsUseCase updateUseCase;

    RagSettingsController(GetRagSettingsUseCase getUseCase, UpdateRagSettingsUseCase updateUseCase) {
        this.getUseCase = getUseCase;
        this.updateUseCase = updateUseCase;
    }

    @Override
    public ResponseEntity<RagSettingsResponse> getRagSettings() {
        return ResponseEntity.ok(toResponse(getUseCase.getForCurrentTenant()));
    }

    @Override
    public ResponseEntity<RagSettingsResponse> updateRagSettings(RagSettingsRequest request) {
        RagSettings updated = updateUseCase.update(toDomain(request));
        return ResponseEntity.ok(toResponse(updated));
    }

    private static RagSettingsResponse toResponse(RagSettings s) {
        RagSettingsResponse r = new RagSettingsResponse(s.minScore(), s.topK(), s.temperature());
        s.systemPrompt().ifPresent(r::setSystemPrompt);
        return r;
    }

    private static RagSettings toDomain(RagSettingsRequest r) {
        Optional<String> prompt = Optional.ofNullable(r.getSystemPrompt()).filter(s -> !s.isBlank());
        return new RagSettings(r.getMinScore(), r.getTopK(), r.getTemperature(), prompt);
    }
}
