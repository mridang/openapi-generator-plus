package com.example.petstore;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Singleton WireMock container with HTTPS support, shared across all test classes.
 */
public final class WireMockContainer {

    private static final GenericContainer<?> INSTANCE;

    static {
        INSTANCE = new GenericContainer<>("wiremock/wiremock:3.13.0")
            .withExposedPorts(8080, 8443)
            .withCopyFileToContainer(
                MountableFile.forHostPath(Path.of("/app/certs/server-keystore.p12")),
                "/tmp/keystore.p12")
            .withCopyFileToContainer(
                MountableFile.forHostPath(Path.of("/app/wiremock/mappings")),
                "/home/wiremock/mappings/")
            .withCommand(
                "--port", "8080",
                "--https-port", "8443",
                "--https-keystore", "/tmp/keystore.p12",
                "--keystore-type", "PKCS12",
                "--keystore-password", "changeit",
                "--key-manager-password", "changeit",
                "--verbose")
            .waitingFor(Wait.forListeningPort())
            .withStartupTimeout(Duration.ofSeconds(60));
        INSTANCE.start();
    }

    private WireMockContainer() {}

    public static String getHttpsUrl() {
        return "https://" + INSTANCE.getHost() + ":" + INSTANCE.getMappedPort(8443);
    }

    public static String getHttpUrl() {
        return "http://" + INSTANCE.getHost() + ":" + INSTANCE.getMappedPort(8080);
    }
}
