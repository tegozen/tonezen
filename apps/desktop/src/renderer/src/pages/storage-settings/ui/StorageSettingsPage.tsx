import { formatGb } from "@/shared/lib/formatTime";
import { SettingsPageLayout, SettingsInfoRow, SettingsSection } from "@/widgets/app-shell";

interface StorageSettingsPageProps {
  usedBytes: number;
  showDeleteConfirm: boolean;
  onBack: () => void;
  onShowDeleteConfirm: (show: boolean) => void;
  onDeleteAll: () => void;
}

export function StorageSettingsPage({
  usedBytes,
  showDeleteConfirm,
  onBack,
  onShowDeleteConfirm,
  onDeleteAll,
}: StorageSettingsPageProps) {
  return (
    <SettingsPageLayout title="Хранилище" onBack={onBack}>
      <SettingsSection title="Загрузки">
        <div className="font-semibold">
          {formatGb(usedBytes)} сохранено офлайн
        </div>
        <SettingsInfoRow
          title="Загрузки"
          subtitle="Офлайн-файлы аудиокниг и музыки"
        />
        <button type="button" className="btn-danger w-full" onClick={() => onShowDeleteConfirm(true)}>
          Удалить все
        </button>
      </SettingsSection>
      {showDeleteConfirm && (
        <div className="sheet-overlay flex items-center justify-center p-5">
          <div className="modal-panel glass-panel">
            <h2 className="text-lg font-semibold">Удалить все загрузки?</h2>
            <p className="mt-2 text-sm text-muted">Все офлайн-файлы будут удалены с этого устройства.</p>
            <div className="mt-4 flex gap-3">
              <button type="button" className="btn-secondary flex-1" onClick={() => onShowDeleteConfirm(false)}>
                Отмена
              </button>
              <button type="button" className="btn-danger flex-1" onClick={onDeleteAll}>
                Удалить все
              </button>
            </div>
          </div>
        </div>
      )}
    </SettingsPageLayout>
  );
}
