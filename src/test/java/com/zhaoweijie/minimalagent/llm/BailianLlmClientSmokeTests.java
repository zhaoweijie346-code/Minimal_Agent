package com.zhaoweijie.minimalagent.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhaoweijie.minimalagent.config.BailianClientConfiguration;
import com.zhaoweijie.minimalagent.config.BailianProperties;
import com.zhaoweijie.minimalagent.context.AgentContext;
import com.zhaoweijie.minimalagent.context.AgentMessage;
import com.zhaoweijie.minimalagent.context.AgentMessageRole;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class BailianLlmClientSmokeTests {

    /**
     * 仅当显式启用 integration profile 且存在 API Key 时调用真实百炼。
     */
    @Test
    void callsRealBailianWhenExplicitlyEnabled() {
        String activeProfiles = firstNonBlank(
                System.getProperty("spring.profiles.active"),
                System.getenv("SPRING_PROFILES_ACTIVE")
        );
        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        assumeTrue(hasProfile(activeProfiles, "integration"));
        assumeTrue(apiKey != null && !apiKey.isBlank());

        BailianProperties properties = new BailianProperties();
        properties.setApiKey(apiKey);
        properties.setBaseUrl(environmentOrDefault(
                "BAILIAN_BASE_URL",
                "https://dashscope.aliyuncs.com/compatible-mode/v1"
        ));
        properties.setModel(environmentOrDefault("BAILIAN_MODEL", "qwen-plus"));
        RestClient restClient = new BailianClientConfiguration()
                .bailianRestClient(RestClient.builder(), properties);
        ObjectMapper objectMapper = new ObjectMapper();
        BailianLlmClient client = new BailianLlmClient(
                restClient,
                properties,
                objectMapper,
                new BailianResponseParser(objectMapper)
        );
        AgentContext context = new AgentContext(
                "Answer briefly.",
                null,
                List.of(new AgentMessage(AgentMessageRole.USER, "Reply with OK.", null, null)),
                null
        );

        LlmResponse response = client.chat(context, List.of());

        assertThat(response.content()).isNotBlank();
    }

    /**
     * 返回第一个非空字符串。
     */
    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    /**
     * 按 Spring Profile 分隔规则检查完整 Profile 名称，避免子串误匹配。
     */
    private boolean hasProfile(String activeProfiles, String expectedProfile) {
        if (activeProfiles == null || activeProfiles.isBlank()) {
            return false;
        }
        return List.of(activeProfiles.split("[,;\\s]+")).contains(expectedProfile);
    }

    /**
     * 读取环境变量，不存在时返回安全默认值。
     */
    private String environmentOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
