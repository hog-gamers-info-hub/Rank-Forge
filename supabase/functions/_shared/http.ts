export type FetchImplementation = (
  input: RequestInfo | URL,
  init?: RequestInit,
) => Promise<Response>;

export class UpstreamTimeoutError extends Error {
  constructor() {
    super("upstream timeout");
    this.name = "UpstreamTimeoutError";
  }
}

export async function fetchWithTimeout(
  fetchImpl: FetchImplementation,
  input: RequestInfo | URL,
  init: RequestInit,
  timeoutMs: number,
): Promise<Response> {
  const controller = new AbortController();
  let didTimeout = false;
  const timeoutId = setTimeout(() => {
    didTimeout = true;
    controller.abort();
  }, timeoutMs);
  try {
    return await fetchImpl(input, { ...init, signal: controller.signal });
  } catch (error) {
    if (didTimeout) throw new UpstreamTimeoutError();
    throw error;
  } finally {
    clearTimeout(timeoutId);
  }
}
