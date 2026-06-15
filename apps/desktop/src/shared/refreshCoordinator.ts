export function createRefreshCoordinator<R>() {
  let inFlight: Promise<R> | null = null;

  return {
    async coalesce(
      needsRefresh: () => boolean,
      refresh: () => Promise<R>,
      current: () => R,
    ): Promise<R> {
      if (inFlight) {
        return inFlight;
      }
      if (!needsRefresh()) {
        return current();
      }
      inFlight = (async () => {
        try {
          return await refresh();
        } finally {
          inFlight = null;
        }
      })();
      return inFlight;
    },
  };
}
