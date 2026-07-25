export class WindowLifecycleManager {
  private quitting = false;

  setQuitting(value: boolean): void {
    this.quitting = value;
  }

  isQuitting(): boolean {
    return this.quitting;
  }

  shouldPreventClose(): boolean {
    return !this.quitting;
  }

  shouldHideOnMinimize(): boolean {
    return true;
  }
}
