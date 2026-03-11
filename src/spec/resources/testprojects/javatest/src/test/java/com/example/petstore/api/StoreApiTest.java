package com.example.petstore.api;

import com.example.petstore.Configuration;
import com.example.petstore.DefaultApiClient;
import com.example.petstore.models.Order;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration tests for the Store API endpoints.
 */
class StoreApiTest {

    private StoreApi api;

    @BeforeEach
    void setUp() {
        String baseUrl = System.getenv("API_BASE_URL");
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "http://localhost:4010";
        }
        Configuration config = new Configuration();
        config.setBaseUrl(baseUrl);
        config.getDefaultHeaders().put("Authorization", "Bearer test-token");
        api = new StoreApi(new DefaultApiClient(), config);
    }

    @Test
    void testGetInventory() throws Exception {
        Map<String, Integer> result = api.getInventory();
        assertNotNull(result);
    }

    @Test
    void testPlaceOrder() throws Exception {
        Order order = new Order();
        order.id = 1L;
        order.petId = 12345L;
        order.quantity = 1;
        order.shipDate = OffsetDateTime.now(ZoneOffset.UTC);
        order.status = Order.StatusEnum.PLACED;
        order.complete = false;

        Order result = api.placeOrder(order);
        assertNotNull(result);

        assertThat(result.id).isNotNull();
    }

    @Test
    void testGetOrderById() throws Exception {
        Order result = api.getOrderById(1L);
        assertNotNull(result);

        assertThat(result.id).isNotNull();
    }

    @Test
    void testDeleteOrder() throws Exception {
        api.deleteOrder(1L);

        assertThat(true).isTrue();
    }
}
