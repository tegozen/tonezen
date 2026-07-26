export interface PlaybackSkipHandlers {
  onSkipNext?: () => boolean;
  onSkipPrevious?: () => boolean;
}
