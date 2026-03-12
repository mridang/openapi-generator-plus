import { PetApi } from '../../src/api/pet-api';
import { BearerAuthenticator } from '../../src/auth/bearer-authenticator';
import { Configuration } from '../../src/configuration';
import { Pet } from '../../src/models';

const baseUrl = process.env.API_BASE_URL || 'http://localhost:4010';
const config = new Configuration({
  baseUrl,
  defaultHeaders: { 'Authorization': 'Bearer test-token' },
});
const api = new PetApi(config);
const auth = new BearerAuthenticator(baseUrl, 'test-token');

describe('PetApi', () => {
  test('addPet', async () => {
    const pet: Pet = {
      id: 12345,
      name: 'TestDog',
      photoUrls: ['http://example.com/photo.jpg'],
      status: 'available',
    };

    const result = await api.addPet(auth, pet);

    expect(result).toBeDefined();
    expect(result.name).toBeDefined();
  });

  test('findPetsByStatus', async () => {
    const result = await api.findPetsByStatus('available');

    expect(Array.isArray(result)).toBe(true);
    expect(result.length).toBeGreaterThan(0);
  });

  test('getPetById', async () => {
    const result = await api.getPetById(1);

    expect(result).toBeDefined();
    expect(result.id).toBeDefined();
    expect(result.name).toBeDefined();
  });

  test('updatePet', async () => {
    const pet: Pet = {
      id: 1,
      name: 'UpdatedDog',
      photoUrls: ['http://example.com/updated.jpg'],
      status: 'pending',
    };

    const result = await api.updatePet(1, pet);

    expect(result).toBeDefined();
  });

  test('deletePet', async () => {
    await api.deletePet(auth, 1);
  });
});
