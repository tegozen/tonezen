interface ToastMessageProps {
  message: string;
}

export function ToastMessage({ message }: ToastMessageProps) {
  return (
    <div className="toast-message-wrap" aria-live="polite" aria-atomic="true">
      <div className="toast-message" role="status">
        {message}
      </div>
    </div>
  );
}
