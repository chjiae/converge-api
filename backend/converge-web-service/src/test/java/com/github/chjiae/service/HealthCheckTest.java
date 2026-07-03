package com.github.chjiae.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 健康检查接口集成测试
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HealthCheckTest extends BaseIntegrationTest {

    @Test
    @Order(1)
    void healthCheck_无需认证_返回UP() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/api/health", String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = assertSuccess(response);
        assertThat(data.get("status").asText()).isEqualTo("UP");
        assertThat(data.get("service").asText()).isEqualTo("converge-api");
        assertThat(data.has("timestamp")).isTrue();
    }
}
