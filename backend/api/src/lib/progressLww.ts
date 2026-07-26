/** @deprecated Prefer progressCas — kept so existing unit imports resolve during cutover. */
export type { ProgressRecord } from "./progressCas.js";
export {
  maybeProgressCasConflict as maybeSkipProgressWrite,
  parseBaseRevision,
} from "./progressCas.js";

import type { ProgressRecord } from "./progressCas.js";

/** Legacy timestamp LWW — unused by API after revision CAS cutover. */
export function mergeProgressLww(
  local: ProgressRecord | null,
  remote: ProgressRecord | null,
): ProgressRecord | null {
  if (!local && !remote) return null;
  if (!local) return remote;
  if (!remote) return local;
  return local.revision >= remote.revision ? local : remote;
}
