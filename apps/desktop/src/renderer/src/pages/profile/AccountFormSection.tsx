import type { ReactNode } from "react";

interface AccountFormSectionProps {
  title: string;
  children: ReactNode;
}

export function AccountFormSection({ title, children }: AccountFormSectionProps) {
  return (
    <section className="account-form-section">
      <h2 className="account-form-section-title">{title}</h2>
      <div className="account-form-section-body">{children}</div>
    </section>
  );
}
