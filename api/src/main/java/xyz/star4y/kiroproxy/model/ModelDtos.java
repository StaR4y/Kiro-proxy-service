package xyz.star4y.kiroproxy.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.time.Instant;
import java.util.List;

public final class ModelDtos {

    private ModelDtos() {
    }

    public record CreateMappingRequest(
        @NotBlank String name,
        @NotBlank String mappingType,
        @NotBlank String sourceModel,
        @NotEmpty List<String> targetModels,
        List<Integer> weights,
        Integer priority,
        List<String> apiKeyIds
    ) {
    }

    public record UpdateMappingRequest(
        String name,
        Boolean enabled,
        String mappingType,
        String sourceModel,
        List<String> targetModels,
        List<Integer> weights,
        Integer priority,
        List<String> apiKeyIds
    ) {
    }

    public record MappingResponse(
        String mappingId,
        String name,
        Boolean enabled,
        String mappingType,
        String sourceModel,
        List<String> targetModels,
        List<Integer> weights,
        Integer priority,
        List<String> apiKeyIds,
        Instant createdAt,
        Instant updatedAt
    ) {
    }
}
