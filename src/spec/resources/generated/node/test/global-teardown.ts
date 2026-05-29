export default async function globalTeardown() {
  for (const key of ['__CHASM_CONTAINER__', '__WIREMOCK_CONTAINER__', '__SQUID_CONTAINER__']) {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const container = (globalThis as any)[key];
    if (container) {
      await container.stop();
    }
  }
}
