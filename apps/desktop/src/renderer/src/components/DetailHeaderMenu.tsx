import { useEffect, useRef, useState } from "react";
import { MoreVerticalIcon } from "./TonezenIcons";

interface DetailHeaderMenuProps {
  showDownload: boolean;
  showRemoveDownload: boolean;
  isListened: boolean;
  onDownload: () => void;
  onToggleListened: () => void;
  onRemoveDownloads: () => void;
}

export function DetailHeaderMenu({
  showDownload,
  showRemoveDownload,
  isListened,
  onDownload,
  onToggleListened,
  onRemoveDownloads,
}: DetailHeaderMenuProps) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const onDocClick = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("click", onDocClick);
    return () => document.removeEventListener("click", onDocClick);
  }, [open]);

  return (
    <div className="relative" ref={ref}>
      <button
        type="button"
        className="icon-circle-btn text-[0]"
        onClick={(e) => {
          e.stopPropagation();
          setOpen((v) => !v);
        }}
        aria-label="Ещё"
      >
        <MoreVerticalIcon className="h-5 w-5 text-base" />
      </button>
      {open && (
        <div className="overflow-menu-popover">
          {showDownload && (
            <MenuItem
              label="Скачать"
              onClick={() => {
                setOpen(false);
                onDownload();
              }}
            />
          )}
          <MenuItem
            label={isListened ? "Отметить не прослушанным" : "Отметить прослушанным"}
            onClick={() => {
              setOpen(false);
              onToggleListened();
            }}
          />
          {showRemoveDownload && (
            <MenuItem
              label="Удалить локальные файлы"
              onClick={() => {
                setOpen(false);
                onRemoveDownloads();
              }}
            />
          )}
        </div>
      )}
    </div>
  );
}

function MenuItem({ label, onClick }: { label: string; onClick: () => void }) {
  return (
    <button
      type="button"
      className="block w-full cursor-pointer rounded-lg px-3 py-2 text-left text-sm hover:bg-surface-muted"
      onClick={onClick}
    >
      {label}
    </button>
  );
}
