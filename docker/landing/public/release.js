const releaseUrl =
  "/rest/v1/app_versions?select=version,changelog_ru,released_at&order=released_at.desc&limit=1";

const versionNodes = document.querySelectorAll("[data-release-version]");
const changelogNode = document.querySelector("[data-release-changelog]");
const dateNode = document.querySelector("[data-release-date]");
const stateNode = document.querySelector("[data-release-state]");

loadRelease();

async function loadRelease() {
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
    const release = Array.isArray(releases) ? releases[0] : null;

    if (!isRelease(release)) {
      throw new Error("Release response is empty or invalid");
    }

    renderRelease(release);
  } catch {
    renderUnavailable();
  }
}

function renderRelease(release) {
  versionNodes.forEach((node) => {
    node.textContent = `Версия ${release.version}`;
  });

  if (dateNode) {
    dateNode.hidden = false;
    dateNode.textContent = `Опубликовано ${formatReleaseDate(release.released_at)}`;
  }

  if (changelogNode) {
    changelogNode.replaceChildren(
      ...release.changelog_ru.map((entry) => {
        const item = document.createElement("li");
        item.textContent = entry;
        return item;
      }),
    );
    changelogNode.hidden = false;
  }

  if (stateNode) {
    stateNode.hidden = true;
  }
}

function renderUnavailable() {
  versionNodes.forEach((node) => {
    node.textContent = "Версия недоступна";
  });

  if (dateNode) {
    dateNode.hidden = true;
    dateNode.textContent = "";
  }

  if (changelogNode) {
    changelogNode.replaceChildren();
    changelogNode.hidden = true;
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
