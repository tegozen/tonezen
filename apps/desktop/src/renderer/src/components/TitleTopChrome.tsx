import type { ReactNode } from "react";

interface TitleTopChromeProps {
  title: string;
  trailing?: ReactNode;
}

export function TitleTopChrome({ title, trailing }: TitleTopChromeProps) {
  return (
    <div className="library-chrome-wrap">
      <div className="library-chrome-shell">
        <div className="title-chrome-row">
          <h1 className="title-chrome-heading">{title}</h1>
          {trailing}
        </div>
      </div>
    </div>
  );
}
