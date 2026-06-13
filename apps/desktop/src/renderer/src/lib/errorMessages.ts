import { strings } from "../i18n/strings";

export function resolveDownloadError(message: string): string {
  switch (message) {
    case "__download_auth_required__":
      return strings.musicPlaybackErrorLogin;
    case "__download_sign_failed__":
    case "__download_no_signed_url__":
    case "__download_transfer_failed__":
      return strings.musicPlaybackErrorDownload;
    case "__download_invalid_path__":
      return strings.downloadFailed;
    default:
      return strings.downloadFailed;
  }
}

export function resolveLoginError(message: string): string {
  if (message.startsWith("__")) {
    return strings.loginFailed;
  }
  return strings.loginFailed;
}
