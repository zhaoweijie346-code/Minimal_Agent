package com.zhaoweijie.minimalagent.tool;

import java.util.List;

/**
 * SearchTool 第一版使用的独立内存检索数据源。
 */
final class MockSearchData {

    /** 可供搜索工具查询的固定文档集合。 */
    private static final List<SearchDocument> DOCUMENTS = List.of(
            new SearchDocument(
                    "Java 21",
                    "Java 21 是长期支持版本，提供虚拟线程、记录模式和序列集合等能力。",
                    List.of("java", "java 21", "jdk 21", "虚拟线程")
            ),
            new SearchDocument(
                    "Spring Boot",
                    "Spring Boot 用于快速创建独立运行、生产级别的 Spring 应用。",
                    List.of("spring", "spring boot", "springboot")
            ),
            new SearchDocument(
                    "AI Agent",
                    "AI Agent 能结合大语言模型、上下文和工具调用自主完成多步任务。",
                    List.of("ai agent", "agent", "智能体")
            ),
            new SearchDocument(
                    "Apache Dubbo",
                    "Dubbo 是面向微服务的高性能 RPC 与服务治理框架。",
                    List.of("dubbo", "apache dubbo", "rpc")
            ),
            new SearchDocument(
                    "Nacos",
                    "Nacos 提供服务发现、配置管理和动态服务管理能力。",
                    List.of("nacos", "服务发现", "配置中心")
            )
    );

    private MockSearchData() {
    }

    /**
     * 获取全部 Mock 搜索文档。
     *
     * @return 不可修改的文档列表
     */
    static List<SearchDocument> documents() {
        return DOCUMENTS;
    }

    /**
     * Mock 搜索索引中的一条文档。
     *
     * @param title    搜索结果标题
     * @param snippet  搜索结果摘要
     * @param keywords 用于简单匹配的关键词
     */
    record SearchDocument(
            /** 搜索结果标题。 */
            String title,
            /** 搜索结果摘要。 */
            String snippet,
            /** 用于简单匹配的关键词。 */
            List<String> keywords
    ) {

        SearchDocument {
            // 固化关键词，避免测试或调用方意外修改共享的 Mock 数据。
            keywords = List.copyOf(keywords);
        }
    }
}
