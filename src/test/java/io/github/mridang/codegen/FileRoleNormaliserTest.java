package io.github.mridang.codegen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.mridang.codegen.FileRoleNormaliser.Classified;
import io.github.mridang.codegen.FileRoleNormaliser.Kind;
import java.io.File;
import java.io.IOException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/** Every path is a file that exists in the committed golden SDK of that language. */
class FileRoleNormaliserTest {

  private static final FileRoleNormaliser.SpecNames SPEC = spec();

  private static FileRoleNormaliser.SpecNames spec() {
    try {
      return FileRoleNormaliser.SpecNames.read(new File("src/spec/resources/specs/petstore/openapi.yaml"));
    } catch (IOException e) {
      throw new java.io.UncheckedIOException(e);
    }
  }

  private static String[] row(String language, String path, Kind kind, String role) {
    return new String[] {language, path, kind.name(), role};
  }

  @TestFactory
  Stream<DynamicTest> classifiesGoldenPaths() {
    return Stream.of(
            row("java", "src/main/java/com/example/petstore/ObjectSerializer.java", Kind.SOURCE, "objectserializer"),
            row("python", "petstore_client/object_serializer.py", Kind.SOURCE, "objectserializer"),
            row("node", "src/object-serializer.ts", Kind.SOURCE, "objectserializer"),
            row("go", "pkg/object_serializer.go", Kind.SOURCE, "objectserializer"),
            row("csharp", "src/PetstoreClient/ObjectSerializer.cs", Kind.SOURCE, "objectserializer"),
            row("rust", "src/object_serializer.rs", Kind.SOURCE, "objectserializer"),
            row("swift", "Sources/PetstoreClient/ObjectSerializer.swift", Kind.SOURCE, "objectserializer"),
            row("elixir", "lib/petstore_client/object_serializer.ex", Kind.SOURCE, "objectserializer"),
            row("php", "lib/ObjectSerializer.php", Kind.SOURCE, "objectserializer"),
            row("ruby", "lib/petstore/client/object_serializer.rb", Kind.SOURCE, "objectserializer"),
            row("dart", "lib/src/object_serializer.dart", Kind.SOURCE, "objectserializer"),
            row("kotlin", "src/main/kotlin/com/example/petstore/ObjectSerializer.kt", Kind.SOURCE, "objectserializer"),
            row("java", "src/main/java/com/example/petstore/api/HeaderSelector.java", Kind.SOURCE, "headerselector"),
            row("csharp", "src/PetstoreClient/Auth/IAuthenticator.cs", Kind.SOURCE, "authenticator"),
            row("go", "pkg/authenticator.go", Kind.SOURCE, "authenticator"),
            row("ruby", "lib/petstore/client/petstore.rb", Kind.SOURCE, "client"),
            row("java", "src/main/java/com/example/petstore/errors/ApiException.java", Kind.SOURCE, "error:api"),
            row("swift", "Sources/PetstoreClient/Errors/ApiError.swift", Kind.SOURCE, "error:api"),
            row("go", "pkg/errors/internal_server_error.go", Kind.SOURCE, "error:internalserver"),
            row("csharp", "src/PetstoreClient/Errors/InternalServerErrorException.cs", Kind.SOURCE, "error:internalserver"),
            row("go", "pkg/pet_api.go", Kind.PER_SPEC, "api:petapi"),
            row("node", "src/api/pet-api.ts", Kind.PER_SPEC, "api:petapi"),
            row("go", "pkg/options/add_pet_options.go", Kind.PER_SPEC, "options:addpetoptions"),
            row("ruby", "lib/petstore/client/api/options/add_pet_options.rb", Kind.PER_SPEC, "options:addpetoptions"),
            row("php", "lib/Api/GetPetByIdServer.php", Kind.PER_SPEC, "server:getpetbyidserver"),
            row("python", "petstore_client/models/pet_with_owner.py", Kind.PER_SPEC, "model:petwithowner"),
            row("swift", "Sources/PetstoreClient/Models/ModelError.swift", Kind.PER_SPEC, "model:error"),
            row("go", "pkg/models/set_pet_avatar_request.go", Kind.PER_SPEC, "model:setpetavatarrequest"),
            row("php", "lib/Models/OrderStatusEnum.php", Kind.PER_SPEC, "enum:orderstatusenum"),
            row("go", "pkg/models/set.go", Kind.UNKNOWN, "model:set"),
            row("python", "petstore_client/__init__.py", Kind.PACKAGE_INDEX, "init"),
            row("rust", "src/api/mod.rs", Kind.PACKAGE_INDEX, "mod"),
            row("node", "src/index.ts", Kind.PACKAGE_INDEX, "index"),
            row("ruby", "lib/petstore_client.rb", Kind.PACKAGE_INDEX, "petstoreclient"),
            row("go", "pkg/base_api_test.go", Kind.TEST, "baseapitest"),
            row("java", "src/test/java/com/example/petstore/ApiClientTest.java", Kind.TEST, "apiclienttest"),
            row("java", "src/spec/java/com/example/petstore/PetstoreSpec.java", Kind.TEST, "petstorespec"),
            row("swift", "Tests/PetstoreClientTests/ApiClientTests.swift", Kind.TEST, "apiclienttests"),
            row("ruby", "sig/petstore/client/api_client.rbs", Kind.TYPE_DECLARATION, "apiclient"),
            row("go", "examples/quickstart/main.go", Kind.EXAMPLE, "main"),
            row("php", "examples/quickstart.php", Kind.EXAMPLE, "quickstart"),
            row("csharp", "examples/Quickstart/Quickstart.csproj", Kind.EXAMPLE, "quickstart"),
            row("java", "src/examples/java/com/example/petstore/examples/Quickstart.java", Kind.EXAMPLE, "quickstart"),
            row("node", "tsconfig.examples.json", Kind.EXAMPLE, "tsconfigexamples"),
            row("rust", "examples/quickstart.rs", Kind.EXAMPLE, "quickstart"),
            row("java", "pom.xml", Kind.MANIFEST, "pomxml"),
            row("go", "go.mod", Kind.MANIFEST, "gomod"),
            row("java", "stray-build-output.log", Kind.UNKNOWN, "straybuildoutput"))
        .map(
            r ->
                DynamicTest.dynamicTest(
                    r[0] + " " + r[1],
                    () -> {
                      Classified c = new FileRoleNormaliser(r[0], SPEC).classify(r[1]);
                      assertThat(c.kind.name()).isEqualTo(r[2]);
                      assertThat(c.role).isEqualTo(r[3]);
                    }));
  }

  @Test
  void spellsEveryCaseStyleTheSame() {
    assertThat(FileRoleNormaliser.roleName("object_serializer")).isEqualTo("objectserializer");
    assertThat(FileRoleNormaliser.roleName("object-serializer")).isEqualTo("objectserializer");
    assertThat(FileRoleNormaliser.roleName("objectSerializer")).isEqualTo("objectserializer");
    assertThat(FileRoleNormaliser.roleName("ObjectSerializer")).isEqualTo("objectserializer");
  }

  @Test
  void rebuildsTheFileNameInTheStyleOfTheLanguage() {
    assertThat(FileRoleNormaliser.expectedFileName("go", "ObjectSerializer")).isEqualTo("object_serializer.go");
    assertThat(FileRoleNormaliser.expectedFileName("node", "object_serializer")).isEqualTo("object-serializer.ts");
    assertThat(FileRoleNormaliser.expectedFileName("csharp", "object-serializer")).isEqualTo("ObjectSerializer.cs");
    assertThat(FileRoleNormaliser.expectedFileName("swift", "OAuth2TokenManager")).isEqualTo("OAuth2TokenManager.swift");
  }

  @Test
  void rejectsAnUnknownLanguage() {
    assertThatThrownBy(() -> new FileRoleNormaliser("cobol", SPEC))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void readsTheSpecNames() {
    assertThat(SPEC.schemas).contains("pet", "petwithowner", "setpetavatarrequest");
    assertThat(SPEC.tags).contains("pet", "store");
    assertThat(SPEC.operations).contains("addpet", "getpetbyid");
  }
}
