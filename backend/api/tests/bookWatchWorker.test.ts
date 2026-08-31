import { describe, expect, it } from "vitest";
import {
  bookNumber,
  bookWatchUrl,
  candidateDedupeKey,
  parseBookWatchPage,
} from "../src/bookWatch/worker.js";

describe("book watch site adapters", () => {
  it("builds encoded provider URLs without accepting arbitrary hosts", () => {
    expect(bookWatchUrl("baza_knig", "Лорд Системы")).toBe(
      "https://baza-knig.top/xfsearch/cikl/%D0%9B%D0%BE%D1%80%D0%B4%20%D0%A1%D0%B8%D1%81%D1%82%D0%B5%D0%BC%D1%8B/",
    );
    expect(bookWatchUrl("allbookerka", "Лорд Системы")).toContain("allbookerka.org/xfsearch/cikl/");
  });

  it("extracts numbered Baza Knig cards and ignores search/tag links", () => {
    const html = `
      <a href="/xfsearch/cikl/Лорд Системы/">Лорд Системы</a>
      <div class="short-title"><a href="/fantastika/123-lord-sistemy-18.html?ref=list#player">18. Лорд Системы. Том 18</a></div>
      <a href="/tags/Автор/">Автор 18</a>`;
    expect(parseBookWatchPage("baza_knig", html)).toEqual([
      {
        provider: "baza_knig",
        url: "https://baza-knig.top/fantastika/123-lord-sistemy-18.html",
        title: "18. Лорд Системы. Том 18",
        author: null,
        number: 18,
      },
    ]);
  });

  it("extracts Allbookerka author/title cards regardless of result order", () => {
    const html = `
      <a href="/top100.html">Топ 100</a>
      <a href="/fantastika/">Фантастика [14870]</a>
      <div class="name"><a href="/fantastika/lord-18.html">Токсик Саша, Яростный Мики - Лорд Системы 18</a></div>
      <div class="name"><a href="/fantastika/lord-14.html">Токсик Саша - Лорд Системы 14</a></div>
      <div class="name"><a href="/fantastika/lord-15.html">Токсик Саша - Лорд Системы 15</a></div>`;
    const result = parseBookWatchPage("allbookerka", html);
    expect(result.map((item) => item.number)).toEqual([18, 14, 15]);
    expect(result[0].author).toBe("Токсик Саша, Яростный Мики");
    expect(result[0].title).toBe("Лорд Системы 18");
  });

  it("deduplicates cross-site results by book number", () => {
    expect(candidateDedupeKey({ number: 18, title: "Лорд Системы 18" })).toBe(
      candidateDedupeKey({ number: 18, title: "Том 18. Край Мира" }),
    );
  });

  it("recognizes common Russian volume markers", () => {
    expect(bookNumber("Лорд Системы 09")).toBe(9);
    expect(bookNumber("Лорд Системы. Том 18")).toBe(18);
    expect(bookNumber("Книга 7. Название")).toBe(7);
  });
});
