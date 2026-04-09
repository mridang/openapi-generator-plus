export default {
  testEnvironment: 'node',
  testMatch: ['**/tests/**/*.test.ts'],
  moduleNameMapper: {
    '^(\\.{1,2}/.*)\\.js$': '$1'
  },
  transform: {
    '^.+\\.tsx?$': ['ts-jest', { diagnostics: false }]
  },
  globalSetup: '<rootDir>/tests/global-setup.ts',
  globalTeardown: '<rootDir>/tests/global-teardown.ts',
  setupFiles: ['<rootDir>/tests/setup.ts'],
  collectCoverage: true,
  coverageDirectory: '.out',
  coverageReporters: ['cobertura'],
  collectCoverageFrom: ['src/**/*.ts', '!src/**/*.d.ts'],
  reporters: ['default', ['jest-junit', { outputDirectory: '.out/reports', outputName: 'junit.xml' }]]
};
