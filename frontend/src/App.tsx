import { useEffect, useState } from "react";
import type { SystemStatus } from "./generated/api-types";

export default function App() {
  const [status, setStatus] = useState<SystemStatus | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    fetch("/api/status", { signal: controller.signal })
      .then((response) => {
        if (!response.ok) {
          throw new Error(`Status request failed: ${response.status}`);
        }
        return response.json() as Promise<SystemStatus>;
      })
      .then(setStatus)
      .catch((cause: unknown) => {
        if (cause instanceof DOMException && cause.name === "AbortError") {
          return;
        }
        setError(cause instanceof Error ? cause.message : "Unknown request error");
      });

    return () => controller.abort();
  }, []);

  if (error) return <main role="alert">{error}</main>;
  if (!status) return <main>Loading system status…</main>;

  return (
    <main>
      <h1>{status.message}</h1>
      <p>Solar power: {status.solarWatts} W</p>
    </main>
  );
}
