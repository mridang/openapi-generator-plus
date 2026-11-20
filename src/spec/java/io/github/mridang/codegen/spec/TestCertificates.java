package io.github.mridang.codegen.spec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Key;
import java.security.KeyStore;
import java.util.Base64;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Generates the TLS material the generated SDK tests use, once per Maven run, so no private key is
 * ever committed. A throwaway CA signs one server certificate whose subject alternative names cover
 * every name the tests reach the mock server under. The JDK's own {@code keytool} does the work, so
 * there is no dependency to add.
 *
 * <p>The directory holds {@code ca.pem}, {@code server.pem} and {@code server-key.pem}. It lives in
 * the system temp directory and is deleted when the JVM exits.
 */
final class TestCertificates {

  /* Every name a test reaches the mock server under. The IP address of the container is left out
   * on purpose: the hostname-mismatch test needs an address the certificate does not list. */
  private static final String SAN =
      "san=dns:chasm,dns:wiremock,dns:localhost,dns:host.docker.internal,ip:127.0.0.1";

  private static final String PASSWORD = "changeit";

  @Nullable private static Path directory;

  private TestCertificates() {}

  /** The directory holding ca.pem, server.pem and server-key.pem; generated on first use. */
  static synchronized Path directory() {
    Path existing = directory;
    if (existing != null) {
      return existing;
    }
    try {
      directory = generate();
    } catch (IOException | java.security.GeneralSecurityException e) {
      throw new IllegalStateException("Failed to generate the test TLS certificates", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while generating the test certificates", e);
    }
    return java.util.Objects.requireNonNull(directory);
  }

  private static Path generate()
      throws IOException, InterruptedException, java.security.GeneralSecurityException {
    Path dir = Files.createTempDirectory("openapi-test-certs");
    Path store = dir.resolve("store.p12");
    Path request = dir.resolve("server.csr");
    Path signed = dir.resolve("server.pem");
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  try (java.util.stream.Stream<Path> paths = Files.walk(dir)) {
                    paths.sorted(java.util.Comparator.reverseOrder()).forEach(TestCertificates::rm);
                  } catch (IOException ignored) {
                    /* a temp directory; the system cleans it up */
                  }
                },
                "test-certificates-cleanup"));

    keytool(
        "-genkeypair", "-alias", "ca", "-keyalg", "RSA", "-keysize", "2048", "-dname", "CN=Test CA",
        "-ext", "bc:c", "-ext", "ku:c=keyCertSign,cRLSign", "-validity", "3650", "-keystore",
        store.toString(), "-storetype", "PKCS12", "-storepass", PASSWORD);
    Path interRequest = dir.resolve("intermediate.csr");
    Path interSigned = dir.resolve("intermediate.pem");
    keytool(
        "-genkeypair", "-alias", "inter", "-keyalg", "RSA", "-keysize", "2048", "-dname",
        "CN=Test Intermediate CA", "-validity", "3650", "-keystore", store.toString(),
        "-storetype", "PKCS12", "-storepass", PASSWORD);
    keytool(
        "-certreq", "-alias", "inter", "-file", interRequest.toString(), "-keystore",
        store.toString(), "-storepass", PASSWORD);
    keytool(
        "-gencert", "-alias", "ca", "-infile", interRequest.toString(), "-outfile",
        interSigned.toString(), "-rfc", "-validity", "3650", "-ext", "bc:c", "-ext",
        "ku:c=keyCertSign,cRLSign", "-keystore", store.toString(), "-storepass", PASSWORD);
    keytool(
        "-exportcert", "-alias", "ca", "-rfc", "-file", dir.resolve("ca.pem").toString(),
        "-keystore", store.toString(), "-storepass", PASSWORD);
    keytool(
        "-importcert", "-noprompt", "-alias", "root", "-file", dir.resolve("ca.pem").toString(),
        "-keystore", store.toString(), "-storepass", PASSWORD);
    keytool(
        "-importcert", "-noprompt", "-alias", "inter", "-file", interSigned.toString(),
        "-keystore", store.toString(), "-storepass", PASSWORD);
    keytool(
        "-genkeypair", "-alias", "server", "-keyalg", "RSA", "-keysize", "2048", "-dname",
        "CN=chasm", "-validity", "3650", "-keystore", store.toString(), "-storetype", "PKCS12",
        "-storepass", PASSWORD);
    keytool(
        "-certreq", "-alias", "server", "-file", request.toString(), "-keystore",
        store.toString(), "-storepass", PASSWORD);
    keytool(
        "-gencert", "-alias", "inter", "-infile", request.toString(), "-outfile",
        signed.toString(), "-rfc", "-validity", "3650", "-ext", SAN, "-ext", "eku=serverAuth",
        "-ext", "ku:c=digitalSignature,keyEncipherment", "-keystore", store.toString(),
        "-storepass", PASSWORD);
    /* keytool writes the whole chain, so server.pem holds the leaf followed by the intermediate
     * that links it to the pinned root. */
    Files.delete(interRequest);
    Files.delete(interSigned);
    KeyStore keyStore = KeyStore.getInstance("PKCS12");
    try (java.io.InputStream in = Files.newInputStream(store)) {
      keyStore.load(in, PASSWORD.toCharArray());
    }
    Key key = keyStore.getKey("server", PASSWORD.toCharArray());
    String pem =
        "-----BEGIN PRIVATE KEY-----\n"
            + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(key.getEncoded())
            + "\n-----END PRIVATE KEY-----\n";
    Files.writeString(dir.resolve("server-key.pem"), pem);

    /* server.pem holds the leaf followed by the intermediate. */
    for (String name : List.of("ca.pem", "server.pem", "server-key.pem")) {
      dir.resolve(name).toFile().setReadable(true, false);
    }
    dir.toFile().setReadable(true, false);
    dir.toFile().setExecutable(true, false);
    Files.delete(store);
    Files.delete(request);
    return dir;
  }

  private static void rm(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException ignored) {
      /* best effort */
    }
  }

  private static void keytool(String... args) throws IOException, InterruptedException {
    java.util.ArrayList<String> command = new java.util.ArrayList<>();
    command.add(Path.of(System.getProperty("java.home"), "bin", "keytool").toString());
    command.addAll(List.of(args));
    Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    if (process.waitFor() != 0) {
      throw new IOException("keytool failed: " + output);
    }
  }
}
