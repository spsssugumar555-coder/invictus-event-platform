import { useCallback, useEffect, useState } from "react";
import { api, errorMessage } from "./api";

/** Loads data once on mount and exposes reload(). */
export function useLoad<T>(fetcher: () => Promise<T>) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [status, setStatus] = useState<number | undefined>(undefined);
  const [loading, setLoading] = useState(true);

  const reload = useCallback(async () => {
    setLoading(true);
    try {
      setData(await fetcher());
      setError(null);
      setStatus(undefined);
    } catch (e) {
      setError(errorMessage(e));
      setStatus((e as { response?: { status?: number } }).response?.status);
    } finally {
      setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    void reload();
  }, [reload]);

  return { data, error, status, loading, reload, setData };
}

/** Loads an authenticated image (needs the Authorization header, so <img src> cannot be used directly). */
export function useBlobUrl(path: string | null, version: unknown = 0) {
  const [url, setUrl] = useState<string | null>(null);
  const [failed, setFailed] = useState(false);
  useEffect(() => {
    if (!path) return;
    let objectUrl: string | null = null;
    let cancelled = false;
    setFailed(false);
    api.get(path, { responseType: "blob" }).then((res) => {
      if (cancelled) return;
      objectUrl = URL.createObjectURL(res.data as Blob);
      setUrl(objectUrl);
    }).catch(() => { if (!cancelled) { setUrl(null); setFailed(true); } });
    return () => { cancelled = true; if (objectUrl) URL.revokeObjectURL(objectUrl); };
  }, [path, version]);
  return { url, failed };
}
