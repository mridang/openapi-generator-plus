defmodule PetstoreClient.Api.PetApiTest do
  use ExUnit.Case, async: false

  setup do
    base_url = System.get_env("API_BASE_URL", "http://localhost:4010")
    auth = PetstoreClient.Auth.BearerAuthenticator.new(base_url, "test-token")

    config =
      PetstoreClient.Configuration.new(
        base_url: base_url,
        default_headers: %{"Authorization" => "Bearer test-token"}
      )

    api = PetstoreClient.Api.PetApi.new(nil, config)

    %{api: api, auth: auth, base_url: base_url}
  end

  test "add_pet creates a new pet", %{api: api, auth: auth} do
    pet = %PetstoreClient.Models.Pet{
      id: 12_345,
      name: "TestDog",
      photo_urls: ["http://example.com/photo.jpg"],
      status: "available"
    }

    assert {:ok, result} = PetstoreClient.Api.PetApi.add_pet(api, pet, auth: auth)
    assert result != nil
  end

  test "find_pets_by_status returns pets by status", %{api: api} do
    options = %PetstoreClient.Api.Options.FindPetsByStatusOptions{status: "available"}
    assert {:ok, result} = PetstoreClient.Api.PetApi.find_pets_by_status(api, options)
    assert is_list(result)
    assert length(result) > 0
  end

  test "get_pet_by_id returns a pet by id", %{api: api} do
    assert {:ok, result} = PetstoreClient.Api.PetApi.get_pet_by_id(api, 1)
    assert result != nil
    assert result.id != nil
    assert result.name != nil
  end

  test "update_pet updates an existing pet", %{api: api} do
    pet = %PetstoreClient.Models.Pet{
      id: 1,
      name: "UpdatedDog",
      photo_urls: ["http://example.com/updated.jpg"],
      status: "pending"
    }

    assert {:ok, result} = PetstoreClient.Api.PetApi.update_pet(api, 1, pet)
    assert result != nil
  end

  test "delete_pet deletes a pet", %{api: api, auth: auth} do
    assert {:ok, _result} =
             PetstoreClient.Api.PetApi.delete_pet(api, 1, %PetstoreClient.Api.Options.DeletePetOptions{}, auth: auth)
  end

  test "set_pet_avatar uploads binary image data", %{api: api} do
    assert {:ok, _result} = PetstoreClient.Api.PetApi.set_pet_avatar(api, 1, <<0xFF, 0xD8, 0xFF>>)
  end

  test "get_pet_avatar downloads the pet avatar as binary", %{api: api} do
    assert {:ok, result} = PetstoreClient.Api.PetApi.get_pet_avatar(api, 1)
    assert result != nil
  end

  test "get_pet_avatar_thumbnail returns a base64-encoded thumbnail", %{api: api} do
    assert {:ok, result} = PetstoreClient.Api.PetApi.get_pet_avatar_thumbnail(api, 1)
    assert result != nil
  end

  test "set_pet_avatar_thumbnail uploads a base64 thumbnail via JSON", %{api: api} do
    request = "iVBORw0KGgoAAAANSUhEUg=="
    assert {:ok, _result} = PetstoreClient.Api.PetApi.set_pet_avatar_thumbnail(api, 1, request)
  end

  test "upload_pet_certificate uploads a certificate via multipart", %{api: api} do
    options = %PetstoreClient.Api.Options.UploadPetCertificateOptions{
      file: "cert-data"
    }

    assert {:ok, result} = PetstoreClient.Api.PetApi.upload_pet_certificate(api, 1, options)
    assert result != nil
  end

  test "upload_pet_document uploads a document with metadata via multipart", %{api: api} do
    options = %PetstoreClient.Api.Options.UploadPetDocumentOptions{
      file: "doc-data",
      document_type: "vaccination_record",
      notes: "Annual checkup"
    }

    assert {:ok, result} = PetstoreClient.Api.PetApi.upload_pet_document(api, 1, options)
    assert result != nil
  end

  @tag :skip
  test "add_pet_photos uploads photos with metadata via multipart" do
    # Prism mock server does not support multipart array fields
  end

  test "download_pet_document downloads a document as binary", %{api: api} do
    assert {:ok, result} = PetstoreClient.Api.PetApi.download_pet_document(api, 1, 1)
    assert result != nil
  end

  @tag :skip
  test "get_pet_photo returns a photo via content negotiation" do
    # Prism returns JSON for image content type
  end

  test "get_pet_passport returns a passport with embedded byte fields", %{api: api} do
    assert {:ok, result} = PetstoreClient.Api.PetApi.get_pet_passport(api, 1)
    assert result != nil
    assert %PetstoreClient.Models.PetPassport{} = result
  end

  @tag :skip
  test "get_pet_tag sends styled path and query parameters" do
    # Prism does not support matrix/label path styles
  end

  @tag :skip
  test "get_external_pet_info uses per-operation server URL" do
    # Per-operation server URL cannot be verified against mock server
  end

  defp new_pet_api_for_mock(status, content_type, body) do
    {:ok, socket} = :gen_tcp.listen(0, [:binary, active: false, reuseaddr: true])
    {:ok, port} = :inet.port(socket)

    spawn(fn ->
      {:ok, client} = :gen_tcp.accept(socket)
      {:ok, _data} = :gen_tcp.recv(client, 0)

      response =
        "HTTP/1.1 #{status} OK\r\nContent-Type: #{content_type}\r\nContent-Length: #{byte_size(body)}\r\n\r\n#{body}"

      :gen_tcp.send(client, response)
      :gen_tcp.close(client)
      :gen_tcp.close(socket)
    end)

    config =
      PetstoreClient.Configuration.new(
        base_url: "http://127.0.0.1:#{port}",
        default_headers: %{}
      )

    PetstoreClient.Api.PetApi.new(nil, config)
  end

  test "error handling 404 not found" do
    api = new_pet_api_for_mock(404, "application/json", ~s({"message":"Pet not found"}))
    assert {:error, _reason} = PetstoreClient.Api.PetApi.get_pet_by_id(api, 99_999)
  end

  test "error handling 500 server error" do
    api = new_pet_api_for_mock(500, "application/json", ~s({"message":"Internal server error"}))
    assert {:error, _reason} = PetstoreClient.Api.PetApi.get_pet_by_id(api, 1)
  end

  test "download binary from mock" do
    api = new_pet_api_for_mock(200, "application/octet-stream", "FAKE_BINARY_DATA")
    assert {:ok, result} = PetstoreClient.Api.PetApi.get_pet_avatar(api, 1)
    assert result != nil
  end

  test "upload multipart from mock" do
    api = new_pet_api_for_mock(200, "application/json", ~s({"code":200,"type":"","message":"success"}))

    options = %PetstoreClient.Api.Options.UploadPetCertificateOptions{
      file: "fake-cert-data"
    }

    assert {:ok, result} = PetstoreClient.Api.PetApi.upload_pet_certificate(api, 1, options)
    assert result != nil
  end

  test "get_pet_by_id_with_http_info returns http metadata", %{api: api} do
    assert {:ok, result} = PetstoreClient.Api.PetApi.get_pet_by_id_with_http_info(api, 1)
    assert result.status_code == 200
    assert result.data != nil
    assert result.raw_body != nil
  end

  test "add_pet_with_http_info returns http metadata", %{api: api, auth: auth} do
    pet = %PetstoreClient.Models.Pet{
      id: 99,
      name: "HttpInfoDog",
      photo_urls: ["http://example.com/photo.jpg"],
      status: "available"
    }

    assert {:ok, result} = PetstoreClient.Api.PetApi.add_pet_with_http_info(api, pet, auth: auth)
    assert result.status_code >= 200
    assert result.status_code < 300
    assert result.data != nil
  end
end
