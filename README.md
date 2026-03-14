# Google Cloud Dataform Plugin for IntelliJ IDEA

Advanced IDE support for [Google Cloud Dataform](https://cloud.google.com/dataform) projects —
syntax highlighting, intelligent completion, BigQuery SQL validation, and full multi-language
support for `.sqlx` files.

![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)
![Platform](https://img.shields.io/badge/platform-IntelliJ%20IDEA-lightgrey.svg)
![Version](https://img.shields.io/badge/version-0.2.8-green.svg)
[![Java](https://img.shields.io/badge/Java-21-blue)](https://www.java.com/)

---

## Features

### SQLX Language Support
- **Syntax highlighting** — dedicated highlighting for `config`, `js`, `pre_operations`,
  `post_operations`, SQL blocks, and template expressions `${...}`
- **Multi-language injection** — each block is parsed by its native language engine:
  - `config { }` → JavaScript (with full TypeScript type inference)
  - `js { }` → JavaScript
  - SQL body / `pre_operations` / `post_operations` → BigQuery SQL dialect
  - `${...}` template expressions → JavaScript

### Smart Code Completion
- **Config block** — property names and enum values inferred from the Dataform proto schema,
  exposed as a TypeScript `.d.ts` declaration file (auto-generated in `.dataform/types/`)
- **Dataform built-in functions** — `ref()`, `resolve()`, `self()`, `assert()`, etc.,
  with signatures and documentation
- **JavaScript symbols** — variables and functions declared in `js { }` blocks and
  `includes/` files are available across the project
- **Workflow settings** — completion and navigation for properties defined in
  `workflow_settings.yaml`
- **BigQuery SQL** — native dialect completion inside SQL blocks

### Navigation & References
- `Ctrl+Click` / `Ctrl+B` on `ref("table_name")` → navigates to the target `.sqlx` file
- Jump to function declarations in `includes/` files
- Workflow settings property references

### Schema Validation
- `workflow_settings.yaml` — JSON schema validation with auto-generated schema from
  the Dataform proto definition
- Config block — TypeScript type checking via `.d.ts` (updated automatically when
  the Dataform core package changes)

### Project Setup
- **New project wizard** — creates a ready-to-use Dataform project structure
- **Dataform CLI integration** — automatic installation and detection of the
  `@dataform/core` npm package
- **Framework detector** — auto-detects existing Dataform projects on import

---

## Requirements

| Requirement | Version |
|---|---|
| IntelliJ IDEA | 2025.3.2 or later |
| Java | 21 or later |
| Node.js | LTS recommended |
| Dataform CLI | Auto-installed by the plugin |

---

## Installation

### From JetBrains Marketplace
1. Open **Settings → Plugins → Marketplace**
2. Search for **Google Cloud Dataform**
3. Click **Install** and restart IntelliJ IDEA

### From disk
1. Download the `.zip` from [GitHub Releases](https://github.com/rejeb/google-cloud-dataform-plugin/releases)
2. **Settings → Plugins → ⚙ → Install Plugin from Disk…**

---

## Getting Started

### New Dataform project
1. **File → New → Project → Dataform**
2. Configure your GCP project and dataset defaults
3. The plugin installs `@dataform/core` and initializes the project structure

### Existing project
Open any folder containing a `workflow_settings.yaml` — the plugin detects it automatically
and activates all features.

### Working with `.sqlx` files
- Use **Ctrl+Space** for completion anywhere in the file
- Each block has independent language support — the `config { }` block behaves like a
  JavaScript object with full type awareness
- Navigate to referenced tables with **Ctrl+Click** on `ref("...")`

### Tip — injection background color
IntelliJ highlights injected language fragments with a colored background by default.
To disable it: **Settings → Editor → Color Scheme → General → Code →
Injected language fragment** → uncheck **Background**.

---

## Building from Source

```bash
# Build
./gradlew build

# Run in a sandboxed IDE instance
./gradlew runIde

# Run tests
./gradlew test
```

### Project structure

```
src/main/java/io/github/rejeb/dataform/
├── language/
│   ├── completion/     # Completion contributors (JS symbols, config, workflow settings)
│   ├── highlight/      # Syntax highlighter, annotators, highlight filters
│   ├── injection/      # Multi-language injectors (SQL, JS, config, template)
│   ├── lexer/          # JFlex-based SQLX lexer
│   ├── parser/         # SQLX parser definition and PSI elements
│   ├── psi/            # PSI node types and manipulators
│   ├── reference/      # Reference contributors (ref(), includes, workflow settings)
│   ├── schema/
│   │   ├── dts/        # TypeScript .d.ts generator for config block types
│   │   ├── json/       # JSON schema generator (proto → JSON Schema)
│   │   └── sql/        # BigQuery dry-run schema extractor
│   └── service/        # Core index, compilation, workflow settings services
├── projectWizard/      # New project wizard and framework detector
└── setup/              # Dataform CLI installer and interpreter manager
```

---

## License

Licensed under the [Apache License, Version 2.0](LICENSE).

---

## Contributing

Issues and pull requests are welcome on the
[GitHub repository](https://github.com/rejeb/google-cloud-dataform-plugin).

## Author

**rbenrejeb** · [@rejeb](https://github.com/rejeb)
