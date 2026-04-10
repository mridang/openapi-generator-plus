using PetstoreClient;
using PetstoreClient.Api;
using PetstoreClient.Auth;
using PetstoreClient.Models;
using Xunit;

#pragma warning disable CS0618 // Intentionally testing deprecated APIs
#pragma warning disable xUnit1004 // Skips are intentional (Prism limitations)

namespace Tests.Api;

[Collection("Prism")]
public class PetApiTest
{
    private readonly PetApi _api;
    private readonly IAuthenticator _auth;

    public PetApiTest(Tests.PrismFixture prism)
    {
        var baseUrl = prism.BaseUrl;
        _auth = new BearerAuthenticator(baseUrl, "test-token");
        var config = Configuration
            .Builder()
            .BaseUrl(baseUrl)
            .DefaultHeader("Authorization", "Bearer test-token")
            .Build();
        _api = new PetApi(new DefaultApiClient(), config);
    }

    [Fact]
    public async Task TestAddPet()
    {
        var pet = new Pet("TestDog", new HashSet<string> { "http://example.com/photo.jpg" })
        {
            Id = 12345L,
            Status = Pet.StatusEnum.Available,
        };

        var result = await _api.AddPetAsync(_auth, pet);

        Assert.NotNull(result);
        Assert.NotNull(result.Name);
    }

    [Fact]
    public async Task TestFindPetsByStatus()
    {
        var result = await _api.FindPetsByStatusAsync(
            new FindPetsByStatusOptions { Status = "available" }
        );

        Assert.NotNull(result);
        Assert.NotEmpty(result);
        Assert.IsType<Pet>(result[0]);
    }

    [Fact]
    public async Task TestGetPetById()
    {
        var result = await _api.GetPetByIdAsync(1L);

        Assert.NotNull(result);
        Assert.NotNull(result.Id);
        Assert.NotNull(result.Name);
    }

    [Fact]
    public async Task TestUpdatePet()
    {
        var pet = new Pet("UpdatedDog", new HashSet<string> { "http://example.com/updated.jpg" })
        {
            Id = 1L,
            Status = Pet.StatusEnum.Pending,
        };

        var result = await _api.UpdatePetAsync(1L, pet);

        Assert.NotNull(result);
    }

    [Fact]
    public async Task TestDeletePet()
    {
        await _api.DeletePetAsync(_auth, 1L);
        Assert.True(true);
    }

    [Fact]
    public async Task TestSetPetAvatar()
    {
        var imageData = new MemoryStream(new byte[] { 0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10 });
        await _api.SetPetAvatarAsync(1L, imageData);
        Assert.True(true);
    }

    [Fact]
    public async Task TestGetPetAvatar()
    {
        var result = await _api.GetPetAvatarAsync(1L);

        Assert.NotNull(result);
        Assert.IsType<Stream>(result, exactMatch: false);
    }

    [Fact]
    public async Task TestGetPetAvatarThumbnail()
    {
        var result = await _api.GetPetAvatarThumbnailAsync(1L);

        Assert.NotNull(result);
        Assert.IsType<byte[]>(result);
    }

    [Fact(Skip = "Prism returns 422 for oneOf byte request bodies")]
    public async Task TestSetPetAvatarThumbnail()
    {
        var thumbnailData = new byte[] { 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A };
        var request = new SetPetAvatarThumbnailRequest(thumbnailData);

        await _api.SetPetAvatarThumbnailAsync(1L, request);
        Assert.True(true);
    }

    [Fact(Skip = "Prism hangs on .NET MultipartFormDataContent requests")]
    public async Task TestUploadPetCertificate()
    {
        var fileData = new MemoryStream(new byte[] { 0x25, 0x50, 0x44, 0x46 });
        var result = await _api.UploadPetCertificateAsync(
            1L,
            new UploadPetCertificateOptions { File = fileData }
        );

        Assert.NotNull(result);
        Assert.IsType<PetstoreClient.Models.ApiResponse>(result);
    }

    [Fact(Skip = "Prism hangs on .NET MultipartFormDataContent requests")]
    public async Task TestUploadPetDocument()
    {
        var fileData = new MemoryStream(new byte[] { 0x25, 0x50, 0x44, 0x46, 0x2D });
        var result = await _api.UploadPetDocumentAsync(
            1L,
            new UploadPetDocumentOptions
            {
                File = fileData,
                DocumentType = "vaccination",
                Notes = "Annual rabies vaccination",
            }
        );

        Assert.NotNull(result);
        Assert.IsType<PetstoreClient.Models.ApiResponse>(result);
    }

    [Fact(Skip = "Prism does not validate multipart array fields correctly")]
    public async Task TestAddPetPhotos()
    {
        var files = new List<Stream>
        {
            new MemoryStream(new byte[] { 0xFF, 0xD8, 0xFF, 0xE0 }),
            new MemoryStream(new byte[] { 0xFF, 0xD8, 0xFF, 0xE1 }),
        };
        var metadata = new PhotoMetadata { Caption = "Pet photo", IsPrimary = true };

        var result = await _api.AddPetPhotosAsync(
            1L,
            new AddPetPhotosOptions { Files = files, Metadata = metadata }
        );

        Assert.NotNull(result);
        Assert.IsType<List<Photo>>(result);
    }

    [Fact]
    public async Task TestDownloadPetDocument()
    {
        var result = await _api.DownloadPetDocumentAsync(1L, 100L);

        Assert.NotNull(result);
        Assert.IsType<Stream>(result, exactMatch: false);
    }

    [Fact(Skip = "Prism returns JSON for image content type")]
    public async Task TestGetPetPhoto()
    {
        var result = await _api.GetPetPhotoAsync(1L, 100L);

        Assert.NotNull(result);
        Assert.IsType<Stream>(result, exactMatch: false);
    }

    [Fact]
    public async Task TestGetPetPassport()
    {
        var result = await _api.GetPetPassportAsync(1L);

        Assert.NotNull(result);
        Assert.IsType<PetPassport>(result);
    }

    [Fact(Skip = "Prism does not support matrix/label parameter styles")]
    public async Task TestGetPetTagStyledParams()
    {
        var result = await _api.GetPetTagAsync(
            5L,
            "cute",
            new GetPetTagOptions
            {
                Colors = new List<string> { "blue", "black" },
                Sizes = new List<string> { "S", "M" },
            }
        );

        Assert.NotNull(result);
    }

    [Fact(Skip = "Per-operation server URL is external and not reachable in test")]
    public async Task TestGetExternalPetInfoUsesPerOperationServer()
    {
        var result = await _api.GetExternalPetInfoAsync(1L);

        Assert.NotNull(result);
    }
}
