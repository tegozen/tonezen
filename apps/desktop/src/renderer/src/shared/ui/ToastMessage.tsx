import styles from "./ToastMessage.module.scss";

interface ToastMessageProps {
  message: string;
}

export function ToastMessage({ message }: ToastMessageProps) {
  return (
    <div className={styles.wrap} aria-live="polite" aria-atomic="true">
      <div className={styles.message} role="status">
        {message}
      </div>
    </div>
  );
}
