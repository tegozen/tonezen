interface EarlierChapterPromptProps {
  visible: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}

export function EarlierChapterPrompt({ visible, onCancel, onConfirm }: EarlierChapterPromptProps) {
  if (!visible) return null;

  return (
    <div className="sheet-overlay flex items-center justify-center p-5">
      <div className="modal-panel glass-panel">
        <h2 className="text-lg font-semibold">Начать с этой главы?</h2>
        <p className="mt-2 text-sm text-muted">
          Вы уже слушали более позднюю главу. Начать выбранную главу с начала?
        </p>
        <div className="mt-4 flex gap-3">
          <button type="button" className="btn-secondary flex-1" onClick={onCancel}>
            Отмена
          </button>
          <button type="button" className="btn-primary flex-1" onClick={onConfirm}>
            Начать
          </button>
        </div>
      </div>
    </div>
  );
}
