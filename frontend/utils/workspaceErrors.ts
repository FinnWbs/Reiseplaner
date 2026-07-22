export const workspaceErrorMessage = (err: any, fallback: string) =>
  err?.data?.message || err?.data?.error || err?.response?._data?.message || err?.message || fallback
