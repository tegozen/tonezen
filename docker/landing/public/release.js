const releaseUrl =
  "/rest/v1/app_versions?select=version,changelog_ru,released_at&order=released_at.desc&limit=3";

const versionNodes = document.querySelectorAll("[data-release-version]");
const releaseListNode = document.querySelector("[data-release-list]");
const stateNode = document.querySelector("[data-release-state]");

loadReleases();

async function loadReleases() {
  try {
    const response = await fetch(releaseUrl, {
      headers: {
        Accept: "application/json",
      },
    });

    if (!response.ok) {
      throw new Error(`Release request failed with ${response.status}`);
    }

    const releases = await response.json();
    const valid = Array.isArray(releases) ? releases.filter(isRelease) : [];

    if (valid.length === 0) {
      throw new Error("Release response is empty or invalid");
    }

    renderReleases(valid);
  } catch {
    renderUnavailable();
  }
}

function renderReleases(releases) {
  const latest = releases[0];

  versionNodes.forEach((node) => {
    node.textContent = `Версия ${latest.version}`;
  });

  if (releaseListNode) {
    releaseListNode.replaceChildren(
      ...releases.map((release) => {
        const article = document.createElement("article");
        article.className = "release-card";

        const version = document.createElement("p");
        version.className = "release-version";
        version.textContent = `Версия ${release.version}`;

        const date = document.createElement("p");
        date.className = "release-date";
        date.textContent = `Опубликовано ${formatReleaseDate(release.released_at)}`;

        const changelog = document.createElement("ul");
        changelog.className = "release-changelog";
        changelog.replaceChildren(
          ...release.changelog_ru.map((entry) => {
            const item = document.createElement("li");
            item.textContent = entry;
            return item;
          }),
        );

        article.append(version, date, changelog);
        return article;
      }),
    );
    releaseListNode.hidden = false;
  }

  if (stateNode) {
    stateNode.hidden = true;
  }
}

function renderUnavailable() {
  versionNodes.forEach((node) => {
    node.textContent = "Версия недоступна";
  });

  if (releaseListNode) {
    releaseListNode.replaceChildren();
    releaseListNode.hidden = true;
  }

  if (stateNode) {
    stateNode.hidden = false;
    stateNode.textContent = "Версия недоступна";
  }
}

function isRelease(value) {
  return (
    value &&
    typeof value.version === "string" &&
    typeof value.released_at === "string" &&
    Array.isArray(value.changelog_ru) &&
    value.changelog_ru.every((entry) => typeof entry === "string" && entry.length > 0)
  );
}

function formatReleaseDate(value) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "недавно";
  }

  return new Intl.DateTimeFormat("ru-RU", {
    day: "numeric",
    month: "long",
    year: "numeric",
  }).format(date);
}
