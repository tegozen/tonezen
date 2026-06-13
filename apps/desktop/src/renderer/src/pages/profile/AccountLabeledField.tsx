import { useId, useState } from "react";
import { EyeIcon, EyeOffIcon } from "../../components/TonezenIcons";

interface AccountLabeledFieldProps {
  label: string;
  value: string;
  onChange?: (value: string) => void;
  type?: "text" | "email" | "password";
  disabled?: boolean;
  showPasswordToggle?: boolean;
}

export function AccountLabeledField({
  label,
  value,
  onChange,
  type = "text",
  disabled = false,
  showPasswordToggle = false,
}: AccountLabeledFieldProps) {
  const inputId = useId();
  const [visible, setVisible] = useState(false);
  const [focused, setFocused] = useState(false);
  const isPassword = type === "password";
  const inputType = isPassword && !visible ? "password" : isPassword ? "text" : type;
  const floated = focused || value.length > 0 || disabled;

  return (
    <label className="account-field" htmlFor={inputId}>
      {floated && <span className="account-field-label">{label}</span>}
      <div className="account-field-control">
        <input
          id={inputId}
          className={`account-field-input ${showPasswordToggle && !disabled ? "pr-11" : ""}`}
          type={inputType}
          value={value}
          disabled={disabled}
          readOnly={disabled}
          placeholder={floated ? undefined : label}
          onFocus={() => setFocused(true)}
          onBlur={() => setFocused(false)}
          onChange={onChange ? (event) => onChange(event.target.value) : undefined}
        />
        {showPasswordToggle && !disabled && (
          <button
            type="button"
            className="account-field-toggle"
            onClick={() => setVisible((current) => !current)}
            aria-label={visible ? label : label}
          >
            {visible ? (
              <EyeOffIcon className="h-[19px] w-[19px] text-muted" />
            ) : (
              <EyeIcon className="h-[19px] w-[19px] text-muted" />
            )}
          </button>
        )}
      </div>
    </label>
  );
}
