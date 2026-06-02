package xyz.star4y.kiroproxy.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProxyModelServiceTest {

    private final ProxyModelService service = new ProxyModelService(new ObjectMapper());

    @Test
    void exposesOfficialHiddenAndPresetModels() {
        JsonNode data = service.models().path("data");
        List<String> ids = data.findValuesAsText("id");

        assertThat(ids).containsExactly(
            "auto",
            "claude-sonnet-4.5",
            "claude-sonnet-4",
            "claude-haiku-4.5",
            "claude-opus-4.5",
            "claude-3.7-sonnet",
            "simple-task",
            "CLAUDE_SONNET_4_20250514_V1_0",
            "CLAUDE_HAIKU_4_5_20251001_V1_0",
            "CLAUDE_3_7_SONNET_20250219_V1_0",
            "gpt-4o",
            "gpt-4",
            "gpt-4-turbo",
            "gpt-3.5-turbo"
        );
        assertThat(data.get(0).path("owned_by").asText()).isEqualTo("kiro-api");
        assertThat(data.get(0).path("description").asText()).isEqualTo("Auto select best model");
        assertThat(data.get(6).path("supportedInputTypes").get(0).asText()).isEqualTo("TEXT");
        assertThat(data.get(6).path("maxOutputTokens").asInt()).isEqualTo(4096);
        assertThat(data.get(10).path("owned_by").asText()).isEqualTo("kiro-proxy");
    }

    @Test
    void mapsSimpleTaskAliasToHaiku() {
        assertThat(KiroModelMapper.map("simple-task")).isEqualTo("claude-haiku-4.5");
    }
}
