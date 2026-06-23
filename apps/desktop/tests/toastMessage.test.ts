import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { ToastMessage } from "../src/renderer/src/components/ToastMessage.js";

describe("ToastMessage", () => {
  it("renders transient app messages as a toast instead of inline error text", () => {
    const html = renderToStaticMarkup(ToastMessage({ message: "Не удалось скачать" }));

    expect(html).toContain('role="status"');
    expect(html).toContain("toast-message");
    expect(html).toContain("Не удалось скачать");
    expect(html).not.toContain("error-text");
  });
});
