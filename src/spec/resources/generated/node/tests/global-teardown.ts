export default async function globalTeardown() {
  for (const key of ['__PRISM_CONTAINER__', '__WIREMOCK_CONTAINER__', '__SQUID_CONTAINER__']) {
    const container = (globalThis as any)[key];
    if (container) {
      await container.stop();
    }
  }
}
