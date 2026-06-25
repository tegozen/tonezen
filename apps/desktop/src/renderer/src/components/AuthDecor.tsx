import type { CSSProperties } from "react";
import { DownloadsIcon, SyncIcon } from "./TonezenIcons";

const COVERS: ReadonlyArray<{
  title: string;
  className: string;
  style: CSSProperties;
  darkText?: boolean;
}> = [
  {
    title: "THE MIDNIGHT LIBRARY",
    className: "auth-cover-midnight z-[1]",
    style: { left: "18px", bottom: "4px", width: "118px", height: "158px", transform: "rotate(-7deg)" },
  },
  {
    title: "ATOMIC HABITS",
    className: "auth-cover-atomic z-[2]",
    style: { left: "50%", top: "0", width: "142px", height: "178px", transform: "translateX(-50%) rotate(1.5deg)" },
    darkText: true,
  },
  {
    title: "THE 4-HOUR BODY",
    className: "auth-cover-body z-[3]",
    style: { right: "18px", bottom: "6px", width: "112px", height: "150px", transform: "rotate(7deg)" },
  },
] as const;

export function AuthStarField() {
  return (
    <div className="auth-star-field" aria-hidden>
      <div className="auth-glow auth-glow-teal" />
      <div className="auth-glow auth-glow-amber" />
      {[
        { top: "10%", left: "18%" },
        { top: "17%", left: "84%" },
        { top: "31%", left: "68%" },
        { top: "48%", left: "24%" },
        { top: "59%", left: "78%" },
        { top: "72%", left: "46%" },
      ].map((star, i) => (
        <span key={i} className="auth-star" style={{ top: star.top, left: star.left }} />
      ))}
    </div>
  );
}

export function AuthIntroPanel() {
  return (
    <div className="space-y-[18px]">
      <h1 className="text-[2rem] font-bold leading-tight">Tonezen</h1>
      <div className="space-y-2.5">
        <h2 className="text-xl font-bold leading-snug">Ваша библиотека, всегда офлайн</h2>
        <p className="text-base leading-relaxed text-muted">
          Скачивайте книги и музыку. Прогресс аудиокниг синхронизируется, когда вы онлайн.
        </p>
      </div>
      <div className="auth-pills">
        <span className="auth-pill auth-pill-teal">
          <DownloadsIcon className="auth-pill-icon" />
          <span className="auth-pill-label">Офлайн-воспроизведение</span>
        </span>
        <span className="auth-pill auth-pill-amber">
          <SyncIcon className="auth-pill-icon" />
          <span className="auth-pill-label">Синхронизация прогресса</span>
        </span>
      </div>
      <AuthMediaStack />
    </div>
  );
}

function AuthMediaStack() {
  return (
    <div className="auth-media-stack">
      {COVERS.map((cover) => (
        <div
          key={cover.title}
          className={`auth-cover ${cover.className}`}
          style={cover.style}
        >
          <span className={cover.darkText ? "text-[#6D4C2F]" : "text-ink"}>{cover.title}</span>
        </div>
      ))}
    </div>
  );
}
