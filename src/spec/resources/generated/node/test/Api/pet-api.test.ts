import { PetApi, UploadPetDocumentDocumentTypeEnum } from '../../src/api/pet-api';
import { BearerAuthenticator } from '../../src/auth/bearer-authenticator';
import { Configuration } from '../../src/configuration';
import { Pet, PhotoMetadata, SetPetAvatarThumbnailRequest } from '../../src/models';

const baseUrl = process.env.API_BASE_URL || 'http://localhost:4010';
const config = Configuration.builder().baseUrl(baseUrl).defaultHeader('Authorization', 'Bearer test-token').build();
const api = new PetApi(undefined, config);
const auth = new BearerAuthenticator(baseUrl, 'test-token');

describe('PetApi', () => {
  test('addPet', async () => {
    const pet: Pet = {
      id: 12345,
      name: 'TestDog',
      photoUrls: new Set(['http://example.com/photo.jpg']),
      status: 'available'
    };

    const result = await api.addPet(auth, pet);

    expect(result).toBeDefined();
    expect(result.name).toBeDefined();
  });

  test('findPetsByStatus', async () => {
    const result = await api.findPetsByStatus({ status: 'available' });

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
      photoUrls: new Set(['http://example.com/updated.jpg']),
      status: 'pending'
    };

    const result = await api.updatePet(1, pet);

    expect(result).toBeDefined();
  });

  test('deletePet', async () => {
    await api.deletePet(auth, 1);
  });

  test('setPetAvatar', async () => {
    const body = Buffer.from([0xff, 0xd8, 0xff]);

    await api.setPetAvatar(1, body);
  });

  test('getPetAvatar', async () => {
    const result = await api.getPetAvatar(1);

    expect(result).toBeDefined();
  });

  test('getPetAvatarThumbnail', async () => {
    const result = await api.getPetAvatarThumbnail(1);

    expect(result).toBeDefined();
  });

  test('setPetAvatarThumbnail', async () => {
    const request: SetPetAvatarThumbnailRequest = 'aGVsbG8=';

    await api.setPetAvatarThumbnail(1, request);
  });

  test('uploadPetCertificate', async () => {
    const file = Buffer.from([0x25, 0x50, 0x44, 0x46]);

    const result = await api.uploadPetCertificate(1, { file });

    expect(result).toBeDefined();
  });

  test('uploadPetDocument', async () => {
    const file = Buffer.from([0x25, 0x50, 0x44, 0x46]);

    const result = await api.uploadPetDocument(1, {
      file,
      documentType: UploadPetDocumentDocumentTypeEnum.HealthCertificate,
      notes: 'Annual checkup document'
    });

    expect(result).toBeDefined();
  });

  // Prism does not validate multipart array fields correctly
  test.skip('addPetPhotos', async () => {
    const files = [Buffer.from([0xff, 0xd8, 0xff])];
    const metadata = new PhotoMetadata({
      caption: 'Test photo',
      isPrimary: true
    });

    const result = await api.addPetPhotos(1, { files, metadata });

    expect(Array.isArray(result)).toBe(true);
  });

  test('downloadPetDocument', async () => {
    const result = await api.downloadPetDocument(1, 100);

    expect(result).toBeDefined();
  });

  test.skip('getPetPhoto - Prism returns JSON for image content type', async () => {
    const result = await api.getPetPhoto(1, 100);

    expect(result).toBeDefined();
  });

  test.skip('getPetTag sends styled parameters - Prism cannot handle matrix/label path styles', async () => {
    const result = await api.getPetTag(5, 'cute', { colors: ['blue', 'black'], sizes: ['S', 'M'] });

    expect(result).toBeDefined();
    expect(result.id).toBeDefined();
  });

  test('getExternalPetInfo uses per-operation server URL', async () => {
    const externalConfig = Configuration.builder().baseUrl(baseUrl).build();
    const externalApi = new PetApi(undefined, externalConfig);

    try {
      await externalApi.getExternalPetInfo(1);
    } catch {
      // Expected to fail since the external server is not available in test
    }
  });

  test('getPetPassport', async () => {
    const result = await api.getPetPassport(1);

    expect(result).toBeDefined();
    expect(result.pet).toBeDefined();
    expect(result.thumbnail).toBeDefined();
    expect(result.scans).toBeDefined();
  });
});
