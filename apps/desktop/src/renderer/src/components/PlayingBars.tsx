export function PlayingBars({ active }: { active: boolean }) {
  return (
    <span className={`playing-bars ${active ? "playing-bars-active" : ""}`} aria-hidden>
      <span />
      <span />
      <span />
    </span>
  );
}
