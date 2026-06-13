export default {
  cacheDirectory: ".jest",
  testEnvironment: "node",
  maxWorkers: "50%",
  testMatch: ["**/test/**/*.test.ts", "**/spec/**/*.spec.ts"],
  moduleNameMapper: {
    "^(\\.{1,2}/.*)\\.js$": "$1",
  },
  transform: {
    "^.+\\.tsx?$": ["ts-jest", { diagnostics: false }],
  },
  globalSetup: "<rootDir>/test/global-setup.ts",
  globalTeardown: "<rootDir>/test/global-teardown.ts",
  setupFiles: ["<rootDir>/test/setup.ts"],
  collectCoverage: true,
  coverageDirectory: ".out",
  coverageReporters: ["cobertura"],
  collectCoverageFrom: ["src/**/*.ts", "!src/**/*.d.ts"],
  reporters: [
    "default",
    [
      "jest-junit",
      { outputDirectory: ".out/reports", outputName: "junit.xml" },
    ],
  ],
};
