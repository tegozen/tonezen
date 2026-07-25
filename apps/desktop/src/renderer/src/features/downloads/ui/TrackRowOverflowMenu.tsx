import { useEffect, useRef, useState, type MouseEvent as ReactMouseEvent, type PointerEvent as ReactPointerEvent } from "react";
import { MoreVerticalIcon } from "@/shared/ui/TonezenIcons";

interface TrackRowOverflowMenuProps {
  showDelete?: boolean;
  isListened?: boolean;
  deleteLabel?: string;
  onDelete?: () => void;
  onToggleListened?: () => void;
}

export function TrackRowOverflowMenu({
  showDelete = false,
  isListened = false,
  deleteLabel = "Удалить загрузку",
  onDelete,
  onToggleListened,
}: TrackRowOverflowMenuProps) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const row = ref.current?.closest(".track-list-row");
    row?.classList.add("track-list-row-menu-open");
    const onDocClick = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("click", onDocClick);
    return () => {
      row?.classList.remove("track-list-row-menu-open");
      document.removeEventListener("click", onDocClick);
    };
  }, [open]);

  if (!onToggleListened && !showDelete) return null;

  return (
    <div className="relative" ref={ref}>
      <button
        type="button"
        className="icon-circle-btn text-[0]"
        onClick={(e) => {
          e.stopPropagation();
          setOpen((value) => !value);
        }}
        onPointerDown={(e) => e.stopPropagation()}
        aria-label="Ещё"
      >
        <MoreVerticalIcon className="h-5 w-5 text-base" />
      </button>
      {open && (
        <div className="overflow-menu-popover" onClick={stopMenuEvent} onPointerDown={stopMenuEvent}>
          {onToggleListened && (
            <MenuItem
              label={isListened ? "Отметить не прослушанным" : "Отметить прослушанным"}
              onClick={() => {
                setOpen(false);
                onToggleListened();
              }}
            />
          )}
          {showDelete && onDelete && (
            <MenuItem
              label={deleteLabel}
              className="text-error"
              onClick={() => {
                setOpen(false);
                onDelete();
              }}
            />
          )}
        </div>
      )}
    </div>
  );
}

function stopMenuEvent(event: ReactMouseEvent | ReactPointerEvent) {
  event.stopPropagation();
}

function MenuItem({
  label,
  onClick,
  className = "",
}: {
  label: string;
  onClick: () => void;
  className?: string;
}) {
  return (
    <button
      type="button"
      className={`block w-full cursor-pointer rounded-lg px-3 py-2 text-left text-sm hover:bg-surface-muted ${className}`}
      onClick={(event) => {
        event.stopPropagation();
        onClick();
      }}
      onPointerDown={stopMenuEvent}
    >
      {label}
    </button>
  );
}
