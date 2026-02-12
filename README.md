# GWFramework

GWFramework is a modular game development framework built on top of libGDX, designed around a microservice-inspired architecture and a configuration-driven approach similar to Java EE and Spring-based systems.

The framework promotes clear separation of responsibilities, scalable project organization, and automation-friendly build pipelines. It is structured for teams and long-term projects where maintainability, distribution workflows, and tooling integration are as important as gameplay logic.

GWFramework is not just a rendering layer. It provides a complete development ecosystem including:

- Modular subprojects
- Asset and resource automation
- Internationalization support
- Distribution packaging
- Built-in desktop tools
- Changelog generation
- Structured versioning

This repository contains the core framework, build orchestration, and supporting utilities.

---

## Architectural Overview

GWFramework applies a microservice-style modularization strategy to game development. Instead of placing all logic in a single project:

- Core functionality is separated into independent subprojects.
- Tools and editors are isolated from runtime code.
- Distribution and packaging are automated through Gradle tasks.
- Configuration profiles allow multiple game setups using the same framework.

This design enables:

- Clean dependency boundaries
- Flexible distribution builds
- Independent testing
- Clear runtime configurations

The result is a scalable structure suited for complex game systems and reusable platform foundations.

---

## Project Structure

At the root level, the project orchestrates multiple submodules using Gradle. The main build file (`build.gradle`) delegates responsibility to specialized Gradle scripts located in the `gradle/` directory.

These include:

- `config.gradle` – configuration profiles
- `versioning.gradle` – version management
- `subprojects.gradle` – subproject wiring
- `testing.gradle` – testing setup
- `i18n.gradle` – internationalization configuration
- `dependency-graph.gradle` – dependency visualization
- `distribution.gradle` – packaging and archives
- `misc.gradle` – utility and editor tasks

The root `build.gradle` acts as a coordinator for all high-level lifecycle operations.

---

## Build and Run Configurations

This project defines several high-level tasks intended for development, testing, packaging, and tooling.

Below is a functional overview of each major task, with a focus on behavior rather than internal Gradle mechanics.

---

## Core Lifecycle Tasks (build.gradle)

### `build`

Primary project build task.

This task:

- Builds all included subprojects
- Assembles the changelog
- Generates runtime scripts
- Produces distributable archives (ZIP and TAR)

Use this when preparing a full release build.

---

### `run`

Runs the game using generated distribution scripts.

Behavior:

- Compiles necessary classes
- Generates runtime scripts
- Executes the platform-specific launcher
- Runs from the generated `bin` directory
- Does not fail the build if the game exits normally

This task ensures that the runtime environment mirrors the packaged distribution structure. It avoids triggering other subproject `run` tasks unintentionally.

Use this for local execution of the game through the distribution layer.

---

### `delivery`

Release-oriented task.

This task:

- Runs all tests
- Assembles the changelog
- Builds ZIP and TAR distribution archives

It is intended for preparing production-ready artifacts.

---

### `clean`

Cleans all subprojects and removes the root build directory.

Use this to reset the workspace to a clean state.

---

### `refreshAssets`

Asset refresh pipeline.

This task:

- Downloads or updates remote resources
- Regenerates asset metadata
- Regenerates asset enum classes

It does not perform a full build. Instead, it refreshes the asset layer only.

Use this when updating resource bundles without rebuilding everything.

---

## Tooling and Utility Tasks (misc.gradle)

The `misc.gradle` file defines additional development and documentation utilities.

---

### Translation Editor

Task: `translationEditor`

- Launches the desktop Translation Editor (JavaFX-based tool)
- Runs independently from the main game
- Uses the translation-editor subproject

Purpose: Provides a graphical tool for managing internationalization content.

---

### Template Editor

Task: `TemplateEditor`

- Runs the desktop template editor

Used for editing reusable content templates.

---

### Map Editor

Task: `MapEditor`

- Launches the 2D Map Editor

Used for level or map design workflows.

---

### World Editor

Task: `WorldEditor`

- Launches the World Editor

Designed for editing larger-scale world configurations.

---

## Changelog and Documentation Tasks

### `assembleChangelog`

Builds a unified changelog file from release fragments.

Behavior:

- Reads release HTML fragments
- Merges summary and detail sections
- Injects them into a master template
- Produces a single consolidated HTML log

This ensures structured release documentation without manual merging.

---

### `viewFrameworkChangelog`

- Builds the changelog
- Opens it in the default system browser

Used for quickly reviewing framework-level changes.

---

### `viewGameChangelog`

- Opens the changelog associated with the active configuration

Useful when managing multiple game configurations within the same framework.

---

## Distribution Model

The framework supports automated distribution packaging.

When running:

- `build`
- `delivery`

The system generates:

- Platform-specific run scripts
- Structured distribution directories
- ZIP and TAR archives

The `run` task executes using the same generated structure to ensure parity between development execution and shipped artifacts.

---

## Configuration Sets

The framework supports configuration-based builds through a `configSet` property.

This enables:

- Multiple games or environments
- Separate changelogs per configuration
- Isolated distribution builds

Configuration switching is handled via Gradle properties and applied consistently across tasks.

---

## Testing

Testing is integrated into the lifecycle:

- Subprojects define their own tests
- The `delivery` task runs all test suites before packaging

This enforces release discipline and ensures artifact reliability.

---

## Versioning

Version management is centralized and applied consistently across:

- Build artifacts
- Distribution naming
- Changelog generation

This ensures traceability across releases.

---

## Intended Use

GWFramework is designed for:

- Structured game projects
- Multi-module development teams
- Tool-assisted content pipelines
- Automated packaging workflows
- Long-term maintainable game platforms

It is particularly well-suited for projects that require:

- Strict modular separation
- Reusable framework components
- Editor tooling integration
- Controlled release processes

---

## Getting Started

1. Clone the repository.
2. Ensure Java is installed.
3. Use Gradle wrapper commands:

    - `./gradlew build`
    - `./gradlew run`
    - `./gradlew delivery`

For editor tools:

- `./gradlew translationEditor`
- `./gradlew MapEditor`
- `./gradlew WorldEditor`

---

## Philosophy

GWFramework treats game development as a structured software system rather than a single executable project.

By combining:

- Modular architecture
- Automated workflows
- Configuration-driven behavior
- Integrated tooling

It creates a professional foundation for scalable game development.

---

## License

This project is licensed under the GNU Affero General Public License v3.0 (AGPL-3.0).

If you modify this software and run it as a service (including over a network), you must make the complete corresponding source code of your modified version available to users of that service, under the same license terms.

See the `LICENSE` file for full details.

---

If you plan to extend or contribute to the framework, consider maintaining consistency with its modular architecture and distribution-oriented workflow model.
