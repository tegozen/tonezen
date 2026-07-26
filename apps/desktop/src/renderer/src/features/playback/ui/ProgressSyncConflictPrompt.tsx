import styles from "./EarlierChapterPrompt.module.css";

export interface ProgressSyncConflictPromptModel {
  localLabel: string;
  serverLabel: string;
}

interface ProgressSyncConflictPromptProps {
  visible: boolean;
  model: ProgressSyncConflictPromptModel | null;
  onCancel: () => void;
  onChooseLocal: () => void;
  onChooseServer: () => void;
}

export function ProgressSyncConflictPrompt({
  visible,
  model,
  onCancel,
  onChooseLocal,
  onChooseServer,
}: ProgressSyncConflictPromptProps) {
  if (!visible || !model) return null;

  return (
    <div className={styles.overlay}>
      <div className={styles.panel}>
        <h2 className="text-lg font-semibold">Где продолжить?</h2>
        <p className="mt-2 text-sm text-muted">
          Прогресс на устройстве и в облаке различается. Выберите точку запуска.
        </p>
        <div className="mt-3 space-y-2 text-sm">
          <p>
            <span className="text-muted">На устройстве: </span>
            {model.localLabel}
          </p>
          <p>
            <span className="text-muted">В облаке: </span>
            {model.serverLabel}
          </p>
        </div>
        <div className={styles.actions}>
          <button type="button" className={styles.cancel} onClick={onCancel}>
            Отмена
          </button>
          <button type="button" className={styles.cancel} onClick={onChooseLocal}>
            На устройстве
          </button>
          <button type="button" className={styles.confirm} onClick={onChooseServer}>
            В облаке
          </button>
        </div>
      </div>
    </div>
  );
}
