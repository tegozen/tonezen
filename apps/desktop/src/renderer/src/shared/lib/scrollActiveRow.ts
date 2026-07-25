export function readScrollPaddingBottom(scrollEl: HTMLElement): number {
  return Number.parseFloat(getComputedStyle(scrollEl).paddingBottom) || 0;
}

/** Keeps a row fully visible above the scroll area's bottom padding (mini-player reserve). */
export function scrollActiveRowAboveBottomPadding(
  scrollEl: HTMLElement,
  rowEl: HTMLElement,
  behavior: ScrollBehavior = "smooth",
): void {
  const padBottom = readScrollPaddingBottom(scrollEl);
  const visibleBottom = scrollEl.clientHeight - padBottom;
  const visibleHeight = Math.max(0, visibleBottom);
  const rowTop = rowEl.offsetTop;
  const rowHeight = rowEl.offsetHeight;
  const rowBottom = rowTop + rowHeight;
  const scrollTop = scrollEl.scrollTop;
  const rowTopInView = rowTop - scrollTop;
  const rowBottomInView = rowBottom - scrollTop;

  if (rowTopInView >= 0 && rowBottomInView <= visibleBottom) return;

  let targetScrollTop = scrollTop;
  if (rowHeight <= visibleHeight) {
    if (rowBottomInView > visibleBottom) {
      targetScrollTop = rowBottom - visibleBottom;
    }
    if (rowTop - targetScrollTop < 0) {
      targetScrollTop = rowTop;
    }
  } else {
    targetScrollTop = rowTop;
  }

  if (targetScrollTop === scrollTop) return;
  scrollEl.scrollTo({ top: Math.max(0, targetScrollTop), behavior });
}
