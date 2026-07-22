# Google Cloud Dataform Plugin for IntelliJ IDEA

A comprehensive IntelliJ IDEA plugin that provides advanced language support for Google Cloud Dataform projects, enabling developers to work efficiently with SQLX files and Dataform workflows.

![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)
![Platform](https://img.shields.io/badge/platform-IntelliJ-lightgrey.svg)
![version](https://img.shields.io/badge/version-0.2.15-green.svg)
[![Java](https://img.shields.io/badge/Java-21-blue)](https://www.java.com/fr/)

## Features

### SQLX Language Support
- **Syntax Highlighting**: Full syntax highlighting for SQLX files with support for SQL, JavaScript, and config blocks
- **Code Completion**: Intelligent code completion for:
  - Dataform built-in functions (`ref()`, `declare()`, `publish()`, etc.)
  - JavaScript symbols and TypeScript definitions
  - Workflow settings and configuration properties
  - JSON schema-based completion for configuration files
- **Code Navigation**: Navigate between references, includes, and declarations
- **BigQuery SQL Support**: Native BigQuery SQL dialect integration with proper syntax validation

### Multi-Language Injection
- **SQL Injection**: BigQuery SQL support within SQLX SQL blocks with template expression handling
- **JavaScript Injection**: Full JavaScript/TypeScript support in JS blocks
- **Config Injection**: JSON-based configuration with schema validation
- **Template Injection**: Support for Dataform template expressions

### Smart References
- **File References**: Navigate to included files and definitions
- **Symbol References**: Jump to function declarations and usages
- **Workflow Settings References**: Link to workflow configuration properties
- **Column Navigation**: `Ctrl+Click` from a column in a SQLX query to its declaration in the source table

### Table Schemas and Column Completion
- **Schema Resolution**: Table schemas are resolved through BigQuery dry-runs of the compiled queries
- **Column Completion**: Completion for columns of tables produced by your Dataform project, including
  qualified names and nested `STRUCT`/`RECORD` fields
- **Safe Introspection**: Pre-operations that would create, alter, insert, update or delete are filtered
  out before a dry-run or a preview query is sent

### Compiled Preview Editor
A split editor next to any `.sqlx` file, with three views:
- **Query**: The compiled SQL, split into pre-operations, incremental pre-operations, the query,
  post-operations and compilation errors
- **Schema**: The resolved output schema of the action
- **Lineage**: The dependency graph around the current file

### Lineage View
- **File Lineage**: Upstream and downstream dependencies of the open file
- **Project Lineage**: A full project graph (`Tools` → `Open Dataform Lineage`, or `Alt+Shift+D`)
- **Interactive Graph**: Pan, zoom, search, minimap, focus mode, and filtering by type, tag and schema
- **Navigation**: Jump from a node to its source file, or run the corresponding action

### GCP Integration
- **Google Cloud Sign-In**: Sign in from the Dataform tool window; the credential is stored in the IDE
  password store and shared across projects and windows. An editor banner prompts for a new sign-in when
  the session expires, so no external `gcloud` login is required
- **Repository Management**: Configure and switch between several Dataform repositories
- **Workspace Management**: Browse, create and select GCP Dataform workspaces
- **Push / Pull**: Synchronize local files with a GCP Dataform workspace
- **Compare with Remote**: Diff a local file against its remote workspace version

### Workflow Execution
- **Run Configurations**: Dedicated Dataform workflow run configuration, by actions, by tags, or the whole project
- **Gutter Icons**: Run an action from a SQLX file, or run all actions sharing its tags
- **Execution View**: Live progress per action, with status, timings and failure reasons
- **BigQuery Job Details**: Job id, bytes processed and billed, duration and child job queries for each executed action

### BigQuery Query Execution
- **Execute Query**: Run the compiled query of an action directly from the preview editor
- **Results Grid**: Paged results shown in the Services view, with query statistics

### SQLX Formatting
- **Built-in Formatter**: Formatting model aware of injected SQL, JavaScript and config blocks
- **Sqlfluff Support**: Optional external formatting through Sqlfluff, configurable in the plugin settings

### Project Setup
- **Module Builder**: Create new Dataform projects with proper structure
- **CLI Integration**: Automatic Dataform CLI installation and setup
- **Project Indexing**: Fast indexing of Dataform core definitions and JavaScript symbols
- **Compilation**: Project compilation integrated with the IDE build, with errors reported in the Build tool window
- **Settings**: Dataform core install path and Sqlfluff configuration under `Settings` → `Tools` → `Dataform`

## Installation

1. Download the plugin from the [JetBrains Marketplace](https://plugins.jetbrains.com/) (once published)
2. Install via IntelliJ IDEA: `Settings` → `Plugins` → `Marketplace` → Search for "Google Cloud Dataform"
3. Restart IntelliJ IDEA

## Requirements

- IntelliJ IDEA 2026.1 or later
- Java 21 or later
- Node.js (for Dataform CLI)
- Dataform CORE
- Dataform CLI

## Usage

### Creating a New Dataform Project
1. `File` → `New` → `Project`
2. Select "Dataform" from the project types
3. Configure your project settings
4. The plugin will automatically set up the project structure and install the Dataform CLI

### Working with SQLX Files
1. Create or open `.sqlx` files in your Dataform project
2. Use code completion (`Ctrl+Space`) for Dataform functions and BigQuery SQL
3. Navigate between references using `Ctrl+Click` or `Ctrl+B`
4. Benefit from syntax validation and error highlighting

### Configuration Files
- `workflow_settings.yaml`: JSON schema validation and completion
- `dataform.json`: Project configuration with schema support
- Package definitions with TypeScript type information

## Development

### Building from Source

```bash
./gradlew build
```

### Running the Plugin in Development Mode

```bash
./gradlew runIde
```

### Project Structure

```
src/main/java/io/github/rejeb/dataform/
├── language/
│   ├── completion/         # Code completion contributors
│   ├── injection/          # Language injection (SQL, JS, Config)
│   ├── lexer/             # Lexer for SQLX files
│   ├── parser/            # Parser definition
│   ├── psi/               # PSI elements
│   ├── reference/         # Reference resolution
│   ├── service/           # Indexing and core services
│   └── util/              # Utility classes
├── projectWizard/         # New project wizard
└── setup/                 # CLI installation and setup
```

## Technologies

- **Kotlin**: Plugin implementation
- **JFlex**: Lexer generation
- **IntelliJ Platform SDK**: Core plugin functionality
- **Google Cloud Dataform API**: Dataform integration
- **BigQuery Dialect**: SQL support

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.

## Contributing

Contributions are welcome! Please feel free to submit issues and pull requests.

## Author

**rbenrejeb**
- GitHub: [@rejeb](https://github.com/rejeb)

## Support

For issues and feature requests, please use the [GitHub issue tracker](https://github.com/rejeb/google-cloud-dataform-plugin/issues).
