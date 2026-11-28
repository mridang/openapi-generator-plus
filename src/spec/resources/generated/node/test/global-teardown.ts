export default async function globalTeardown() {
  for (const container of [
    globalThis.__CHASM_CONTAINER__,
    globalThis.__SQUID_CONTAINER__,
  ]) {
    if (container) {
      await container.stop();
    }
  }
}
