# SearchMavenArtifactEasy

[![Plugin Version](https://img.shields.io/badge/version-0.1--beta-blue.svg)](https://plugins.jetbrains.com/plugin/31454-searchmavenartifacteasy)
[![JetBrains Marketplace](https://img.shields.io/jetbrains/plugin/v/31454?label=Marketplace&color=green)](https://plugins.jetbrains.com/plugin/31454-searchmavenartifacteasy)
[![IntelliJ Platform](https://img.shields.io/badge/IntelliJ_IDEA-2025.3%2B-000000.svg)](https://www.jetbrains.com/idea/)
[![Build](https://img.shields.io/badge/build-Gradle-006600.svg)](https://gradle.org/)

An IntelliJ IDEA plugin that searches Maven Central artifacts directly within the IDE and adds dependencies to `pom.xml` with a single click.

---

## Features

- **Search artifacts** -- Search Maven Central by keyword with pagination support
- **Browse versions** -- View all available versions for any artifact, sorted by recency
- **XML preview** -- Live preview of the `<dependency>` XML before adding
- **Add to pom.xml** -- Insert the dependency directly into `pom.xml` via PSI operations
- **Copy to clipboard** -- Copy the dependency XML snippet to the system clipboard
- **Async requests** -- All network requests run in background tasks without blocking the IDE

---

## Quick Start

### Installation

#### From JetBrains Marketplace (Recommended)

1. Open **Settings/Preferences -> Plugins**
2. Click **Marketplace** tab
3. Search for `SearchMavenArtifactEasy`
4. Click **Install**
5. Restart the IDE

Or directly install from the plugin page: [SearchMavenArtifactEasy on JetBrains Marketplace](https://plugins.jetbrains.com/plugin/31454-searchmavenartifacteasy)

#### From Disk

1. Download the plugin ZIP from [Releases](https://github.com/xiaowei-ce/SearchMavenArtifactEasy/releases)
2. Open **Settings/Preferences -> Plugins**
3. Click the gear icon -> **Install Plugin from Disk...**
4. Select the downloaded ZIP file
5. Restart the IDE

#### Build from Source

```bash
./gradlew buildPlugin
```

The built plugin package will be at `build/libs/SearchMavenArtifactEasy-*.jar`.

### Usage

1. Open any Maven project (containing a `pom.xml`)
2. Click **Tools -> Search Maven Artifact Easy** (or search for `Search Maven Artifact Easy` via Find Action)
3. Enter a keyword (e.g. `lombok`, `gson`) in the search field and click **Search**
4. Select an artifact from the results table
5. Choose a version from the dropdown (or select `[No Version / Omit]` to leave the version tag out)
6. Click **Add to pom.xml** to write the dependency to the currently open `pom.xml`
7. Or click **Copy XML** to copy the dependency snippet to the clipboard

---

## Project Structure

```
src/main/java/cc/xiaowei/
├── MavenSearchAction.java      # AnAction entry point, registered in Tools menu
├── MavenSearchDialog.java      # DialogWrapper with UI layout and event handling
├── service/
│   ├── MavenSearchService.java # Async search and version fetching
│   └── PomXmlManager.java      # PSI operations for pom.xml read/write
└── utils/
    ├── FetchUtils.java         # HTTP client and JSON utilities
    └── StringUtils.java        # XML escaping and string helpers
```

---

## Tech Stack

| Component | Technology |
|-----------|------------|
| Language | Java 21+ (Kotlin JVM Toolchain) |
| IDE Platform | IntelliJ Platform SDK (2025.3+) |
| Build | Gradle (9.2.1) + IntelliJ Platform Gradle Plugin (2.10.5) |
| JSON | Gson (bundled with IDE) |
| HTTP Client | `java.net.http.HttpClient` (Java 11+) |
| PSI Framework | `XmlElementFactory`, `WriteCommandAction` |

---

## License

This project is licensed under the Apache License 2.0. See [LICENSE](LICENSE) for details.

---

## Author

- **xiaowei** ([GitHub](https://github.com/xiaowei-ce))
