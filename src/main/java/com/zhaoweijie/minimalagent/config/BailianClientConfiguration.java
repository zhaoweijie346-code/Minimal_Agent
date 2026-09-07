package com.zhaoweijie.minimalagent.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

/**
 * 百炼 OpenAI Compatible HTTP Client 配置。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "llm.provider", havingValue = "bailian", matchIfMissing = true)
public class BailianClientConfiguration {

    /**
     * 创建带连接与读取超时的百炼 RestClient。
     *
     * @param builder    Spring Boot 提供的 RestClient Builder
     * @param properties 百炼连接配置
     * @return 百炼专用 RestClient
     */
    @Bean
    @Qualifier("bailianRestClient")
    public RestClient bailianRestClient(
            RestClient.Builder builder,
            BailianProperties properties
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getReadTimeout());

        return builder
                .baseUrl(removeTrailingSlash(properties.getBaseUrl()))
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * 移除 Base URL 尾部斜杠，确保相对请求路径稳定拼接。
     */
    private String removeTrailingSlash(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("llm.base-url must not be blank");
        }
        return baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
    }
}
