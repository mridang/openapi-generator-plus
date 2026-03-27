import { StoreApi } from '../../src/api/store-api';
import { Configuration } from '../../src/configuration';
import { Order } from '../../src/models';

const config = Configuration.builder()
  .baseUrl(process.env.API_BASE_URL || 'http://localhost:4010')
  .defaultHeader('Authorization', 'Bearer test-token')
  .build();
const api = new StoreApi(config);

describe('StoreApi', () => {
  test('getInventory', async () => {
    const result = await api.getInventory();

    expect(result).toBeDefined();
    expect(typeof result).toBe('object');
  });

  test('placeOrder', async () => {
    const order: Order = {
      id: 1,
      petId: 12345,
      quantity: 1,
      shipDate: new Date().toISOString(),
      status: 'placed',
      complete: false
    };

    const result = await api.placeOrder(order);

    expect(result).toBeDefined();
    expect(result.id).toBeDefined();
  });

  test('getOrderById', async () => {
    const result = await api.getOrderById(1);

    expect(result).toBeDefined();
    expect(result.id).toBeDefined();
  });

  test('deleteOrder', async () => {
    await api.deleteOrder(1);
  });
});
