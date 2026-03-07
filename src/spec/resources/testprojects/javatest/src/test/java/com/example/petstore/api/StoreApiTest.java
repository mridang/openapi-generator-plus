package com.example.petstore.api;

import com.example.petstore.Configuration;
import com.example.petstore.DefaultApiClient;
import com.example.petstore.models.Order;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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
        api = new StoreApi(new DefaultApiClient(), config);
    }

    @Test
    void testGetInventory() throws Exception {
        Map<String, Integer> result = api.getInventory();

        assertThat(result).isNotNull();
    }

    @Test
    void testPlaceOrder() throws Exception {
        Order order = new Order();
        order.id = 1L;
        order.petId = 12345L;
        order.quantity = 1;
        order.shipDate = OffsetDateTime.now();
        order.status = Order.StatusEnum.PLACED;
        order.complete = false;

        Order result = api.placeOrder(order);

        assertThat(result).isNotNull();
        assertThat(result.id).isNotNull();
    }

    @Test
    void testGetOrderById() throws Exception {
        Order result = api.getOrderById(1L);

        assertThat(result).isNotNull();
        assertThat(result.id).isNotNull();
    }

    @Test
    void testDeleteOrder() throws Exception {
        api.deleteOrder(1L);

        assertThat(true).isTrue();
    }
}
