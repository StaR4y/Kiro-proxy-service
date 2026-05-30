package xyz.star4y.kiroproxy.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;

class ModelMappingServiceTest {

    @Test
    void appliesHighestPriorityWildcardRule() {
        ModelMappingRepository repository = Mockito.mock(ModelMappingRepository.class);
        ModelMappingEntity rule = new ModelMappingEntity();
        rule.setEnabled(true);
        rule.setMappingType("replace");
        rule.setSourceModel("gpt-*");
        rule.setTargetModels("[\"claude-sonnet-4.5\"]");
        rule.setPriority(1);
        when(repository.findAllByEnabledTrueOrderByPriorityAsc()).thenReturn(Flux.just(rule));

        ModelMappingService service = new ModelMappingService(repository, new ObjectMapper());
        service.refresh().block();

        assertThat(service.apply("gpt-4o", "key-1")).isEqualTo("claude-sonnet-4.5");
        assertThat(service.apply("claude-haiku-4.5", "key-1")).isEqualTo("claude-haiku-4.5");
    }
}
