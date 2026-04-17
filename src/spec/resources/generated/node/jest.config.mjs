export default {
  testEnvironment: 'node',
  testMatch: ['**/test/**/*.test.ts'],
  moduleNameMapper: {
    '^(\\.{1,2}/.*)\\.js$': '$1'
  },
  transform: {
    '^.+\\.tsx?$': ['ts-jest', { diagnostics: false }]
  },
  globalSetup: '<rootDir>/test/global-setup.ts',
  globalTeardown: '<rootDir>/test/global-teardown.ts',
  setupFiles: ['<rootDir>/test/setup.ts'],
  collectCoverage: true,
  coverageDirectory: '.out',
  coverageReporters: ['cobertura'],
  collectCoverageFrom: ['src/**/*.ts', '!src/**/*.d.ts'],
  reporters: ['default', ['jest-junit', { outputDirectory: '.out/reports', outputName: 'junit.xml' }]]
};
