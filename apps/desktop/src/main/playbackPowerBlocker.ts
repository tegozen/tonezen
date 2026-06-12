import { powerSaveBlocker } from "electron";

export class PlaybackPowerBlocker {
  private id: number | null = null;

  setActive(active: boolean): void {
    if (active && this.id == null) {
      this.id = powerSaveBlocker.start("prevent-app-suspension");
    } else if (!active && this.id != null) {
      powerSaveBlocker.stop(this.id);
      this.id = null;
    }
  }

  stop(): void {
    if (this.id != null) {
      powerSaveBlocker.stop(this.id);
      this.id = null;
    }
  }
}
