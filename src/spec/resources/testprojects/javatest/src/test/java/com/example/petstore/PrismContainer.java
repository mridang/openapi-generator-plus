package com.example.petstore;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Path;

/**
 * Singleton Prism mock server container shared across all test classes.
 */
public final class PrismContainer {

    private static final GenericContainer<?> INSTANCE;

    static {
        INSTANCE = new GenericContainer<>("stoplight/prism:5")
            .withExposedPorts(4010)
            .withCopyFileToContainer(
                MountableFile.forHostPath(Path.of("/app/specs/openapi.yaml")),
                "/tmp/openapi.yaml")
            .withCommand("mock", "-h", "0.0.0.0", "/tmp/openapi.yaml")
            .waitingFor(Wait.forLogMessage(".*Prism is listening.*", 1));
        INSTANCE.start();
    }

    private PrismContainer() {}

    public static String getBaseUrl() {
        return "http://" + INSTANCE.getHost() + ":" + INSTANCE.getMappedPort(4010);
    }
}
