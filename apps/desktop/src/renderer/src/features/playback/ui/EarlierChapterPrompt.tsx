import styles from "./EarlierChapterPrompt.module.css";

interface ConfirmPlaybackPromptProps {
  visible: boolean;
  title: string;
  message: string;
  onCancel: () => void;
  onConfirm: () => void;
}

function ConfirmPlaybackPrompt({
  visible,
  title,
  message,
  onCancel,
  onConfirm,
}: ConfirmPlaybackPromptProps) {
  if (!visible) return null;

  return (
    <div className={styles.overlay}>
      <div className={styles.panel}>
        <h2 className="text-lg font-semibold">{title}</h2>
        <p className="mt-2 text-sm text-muted">{message}</p>
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

interface EarlierChapterPromptProps {
  visible: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}

export function EarlierChapterPrompt({ visible, onCancel, onConfirm }: EarlierChapterPromptProps) {
  return (
    <ConfirmPlaybackPrompt
      visible={visible}
      title="Начать с этой главы?"
      message="Вы уже слушали более позднюю главу. Начать выбранную главу с начала?"
      onCancel={onCancel}
      onConfirm={onConfirm}
    />
  );
}

interface EarlierCycleBookPromptProps {
  visible: boolean;
  laterBookTitle: string;
  onCancel: () => void;
  onConfirm: () => void;
}

export function EarlierCycleBookPrompt({
  visible,
  laterBookTitle,
  onCancel,
  onConfirm,
}: EarlierCycleBookPromptProps) {
  return (
    <ConfirmPlaybackPrompt
      visible={visible}
      title="Начать эту книгу?"
      message={`Последнее прослушивание в цикле — «${laterBookTitle}». Начать выбранную книгу?`}
      onCancel={onCancel}
      onConfirm={onConfirm}
    />
  );
}
