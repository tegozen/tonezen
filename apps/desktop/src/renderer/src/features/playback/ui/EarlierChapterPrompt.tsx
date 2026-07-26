import styles from "./EarlierChapterPrompt.module.css";

interface EarlierChapterPromptProps {
  visible: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}

export function EarlierChapterPrompt({ visible, onCancel, onConfirm }: EarlierChapterPromptProps) {
  if (!visible) return null;

  return (
    <div className={styles.overlay}>
      <div className={styles.panel}>
        <h2 className="text-lg font-semibold">Начать с этой главы?</h2>
        <p className="mt-2 text-sm text-muted">
          Вы уже слушали более позднюю главу. Начать выбранную главу с начала?
        </p>
        <div className={styles.actions}>
          <button type="button" className={styles.cancel} onClick={onCancel}>
            Отмена
          </button>
          <button type="button" className={styles.confirm} onClick={onConfirm}>
            Начать
          </button>
        </div>
      </div>
    </div>
  );
}
