package com.example.petstore;

import com.example.petstore.api.StoreApi;
import com.example.petstore.model.Order;
import com.example.petstore.ApiClient;
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
        ApiClient client = new ApiClient();
        client.setBasePath(baseUrl);
        api = new StoreApi(client);
    }

    @Test
    void testGetInventory() throws Exception {
        Map<String, Integer> result = api.getInventory();

        assertThat(result).isNotNull();
    }

    @Test
    void testPlaceOrder() throws Exception {
        Order order = new Order();
        order.setId(1L);
        order.setPetId(12345L);
        order.setQuantity(1);
        order.setShipDate(OffsetDateTime.now());
        order.setStatus(Order.StatusEnum.PLACED);
        order.setComplete(false);

        Order result = api.placeOrder(order);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isNotNull();
    }

    @Test
    void testGetOrderById() throws Exception {
        Order result = api.getOrderById(1L);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isNotNull();
    }

    @Test
    void testDeleteOrder() throws Exception {
        api.deleteOrder(1L);

        assertThat(true).isTrue();
    }
}
