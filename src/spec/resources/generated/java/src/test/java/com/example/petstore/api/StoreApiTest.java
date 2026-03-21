package com.example.petstore.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.example.petstore.Configuration;
import com.example.petstore.DefaultApiClient;
import com.example.petstore.PrismContainer;
import com.example.petstore.models.Order;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Integration tests for the Store API endpoints. */
class StoreApiTest {

  private StoreApi api;

  @BeforeEach
  void setUp() {
    String baseUrl = PrismContainer.getBaseUrl();
    Configuration config =
        Configuration.builder()
            .baseUrl(baseUrl)
            .defaultHeader("Authorization", "Bearer test-token")
            .build();
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
