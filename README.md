# Google Cloud Dataform Plugin for IntelliJ IDEA

A comprehensive IntelliJ IDEA plugin that provides advanced language support for Google Cloud Dataform projects, enabling developers to work efficiently with SQLX files and Dataform workflows.

![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)
![Platform](https://img.shields.io/badge/platform-IntelliJ-lightgrey.svg)
![version](https://img.shields.io/badge/version-0.2.24-green.svg)
[![Java](https://img.shields.io/badge/Java-21-blue)](https://www.java.com/fr/)
[![JetBrains Marketplace](https://img.shields.io/jetbrains/plugin/v/30020?label=JetBrains%20Marketplace)](https://plugins.jetbrains.com/plugin/30020)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/30020)](https://plugins.jetbrains.com/plugin/30020)

## Features

### SQLX Language Support
- **Syntax Highlighting**: Full syntax highlighting for SQLX files with support for SQL, JavaScript, and config blocks
- **Code Completion**: Intelligent code completion for:
  - Dataform built-in functions (`ref()`, `declare()`, `publish()`, etc.)
  - JavaScript symbols and TypeScript definitions
  - Workflow settings and configuration properties
  - JSON schema-based completion for configuration files
  - `ref()` completion listing the actions declared in the project, in SQLX and JavaScript files
  - Dataform template expressions (`${...}`), with the `$` key opening completion automatically
  - `config { }` properties, their enumerated values, and the tags already used in the project
  - Columns of the action in `config { }`: keys of the `columns` map, `clusterBy`, `uniqueKey`, the
    `assertions` block, and the partitioning expressions BigQuery accepts in `partitionBy`
- **Code Navigation**: Navigate between references, includes, and declarations
- **BigQuery SQL Support**: Native BigQuery SQL dialect integration with proper syntax validation
- **Declared Variables**: `DECLARE` variables defined in pre-operations resolve inside the query

### Quick Documentation
`Ctrl+Q` on:
- **Table References**: `ref()` and resolved tables show the target action, its type, schema and columns
- **Columns**: Column name, type, mode and description from the resolved table schema
- **BigQuery Functions**: Built-in documentation for the BigQuery function catalog, with signature,
  description and examples
- **Config Keys**: What a `config { }` key expects and, for column-naming keys, the columns the
  action actually produces

### Inline Expression Values
Dataform expressions are folded to their evaluated value in the editor, the way IntelliJ shows
`.properties` values instead of their key. `Ctrl+.` expands a fold back to the source.
- **`${...}` in SQL and `pre/post_operations` blocks**, and in query template literals of `.js` /
  `.ts` definition files: `FROM ${ref("users")}` reads as `FROM my-project.my_dataset.users`
- **`workflow_settings.yaml` references** in `config { }` and `js { }` blocks:
  `dataform.projectConfig.vars.env` reads as its YAML value
- **`includes/` exports** used in `config { }` and `js { }` blocks, such as
  `columns: my_descriptions.columns_descriptions`, read as the exported value
- An expression whose value is long or multi-line is painted **over several lines**, in place,
  keeping the value's own formatting. Code sharing the line, such as `columns: ` and the trailing
  comma of a config property, is repainted around it. The gutter icon brings the source back
- Shorter values fold to a single line; `Ctrl+Q` on any expression shows the full value
- Only expressions with a single deterministic value are folded: the Dataform graph functions
  (`ref()`, `resolve()`, `self()`, `name()`, `schema()`, `database()`), workflow settings, and your
  own `includes/` code. Expressions depending on the run mode (`when()`, `incremental()`) or defining
  actions (`publish()`, `operate()`, `assert()`, `declare()`) stay visible — but the deterministic
  `${...}` nested inside them, at any depth, are still folded
- Values are computed by running the expression with the Node.js interpreter configured for the
  project, so `includes/` helper functions, string building and any other JavaScript resolve exactly
  as Dataform would evaluate them. `ref()` and `self()` use the last compiled graph, so they resolve
  after a build; nothing is folded when a value cannot be computed.
- Because project JavaScript is executed as you type, the feature can be turned off under
  **Settings ▸ Tools ▸ Dataform ▸ Fold Dataform expressions to their evaluated value**.

### Multi-Language Injection
- **SQL Injection**: BigQuery SQL support within SQLX SQL blocks with template expression handling
- **JavaScript Injection**: Full JavaScript/TypeScript support in JS blocks
- **Config Injection**: JSON-based configuration with schema validation
- **Template Injection**: Support for Dataform template expressions
- **JS Query Injection**: BigQuery SQL injected in query template literals written in `.js` / `.ts`
  files, with working `ref()` resolution and column completion

### Smart References
- **File References**: Navigate to included files and definitions
- **Symbol References**: Jump to function declarations and usages
- **Workflow Settings References**: Link to workflow configuration properties
- **Column Navigation**: `Ctrl+Click` from a column in a SQLX query to its declaration in the source table

### Column Refactoring and Usages
- **Rename Downstream Usages**: After a column is renamed in its action and saved, the new name is
  highlighted while downstream actions still read the old one; its quick fix, also offered in a
  balloon, renames those reads through the rename preview
- **Rename Column** (`Shift+F6`): Renames a column of a Dataform table in the action declaring it and
  in every action reading it, following the column lineage. When the column comes from a `SELECT *`,
  a dialog asks whether to expand the star or declare the new name locally
- **Find Usages**: Works on Dataform tables, columns, the aliases declaring them, and fields of
  `STRUCT` columns
- **Show Column Usages** (`Ctrl+Alt+Shift+U`): A popup listing where the column under the caret is
  declared and every place that reads it
- **Diagnose Dataform Column Resolution**: An editor action reporting step by step how the column
  under the caret is resolved

### Errors and Validation
- **In-Place Errors**: Dataform compilation errors and BigQuery dry-run errors are placed on the
  tokens they refer to, with inline chips in the editor
- **Column Impact Analysis**: On save, columns renamed, removed or retyped are compared with the
  actions reading them; the broken ones are listed per file in the Build tool window, and their
  errors in the editor name the change behind them
- **Unplaced Errors Banner**: Errors that cannot be located in the file are listed in an editor banner
- **Validation**: Dataform validation problems are highlighted at their exact range, with hints and
  quick fixes, once typing stops
- **`partitionBy` Inspection**: Reports a `partitionBy` written as an object and replaces it with
  the BigQuery partitioning expression it stands for
- **`js { }` Symbols**: Functions and constants of a `js` block used from `${...}` are no longer
  reported as unused

### Dataform Unit Tests
- **Create Dataform Test**: Generates `definitions/tests/test_<action>.sqlx` for a table or view,
  from the project view or the editor menu
- **Test Editing**: Completion of the tested dataset, input names and column aliases, and
  `Ctrl+Click` from an alias to the column it stands for
- **Run Tests on BigQuery**: A `Dataform Unit Tests` run configuration with gutter icons; results
  appear in the IDE test runner, with expected and actual rows side by side on failure

### Table Schemas and Column Completion
- **Schema Resolution**: Table schemas are resolved through BigQuery dry-runs of the compiled queries,
  for tables, views and operations declaring `hasOutput: true`
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
- **Column-Level Lineage**: Expand a node to see its columns and follow column-to-column edges,
  derived from the compiled `SELECT` statements, with a confidence level per edge
- **Interactive Graph**: Pan, zoom, search, minimap, focus mode, and filtering by type, tag and schema
- **Navigation**: Jump from a node to its source file, or run the corresponding action

### GCP Integration
- **Google Cloud Sign-In**: Sign in from the Dataform tool window; the credential is stored in the IDE
  password store and shared across projects and windows. An editor banner prompts for a new sign-in when
  the session expires, so no external `gcloud` login is required
- **Repository Management**: Configure and switch between several Dataform repositories
- **Service Account**: Pick the service account used by a repository from the project's accounts,
  listed through the IAM API, so workflow invocations run with the right identity
- **Workspace Management**: Browse, create and select GCP Dataform workspaces
- **Push / Pull**: Synchronize local files with a GCP Dataform workspace
- **Compare with Remote**: Diff a local file against its remote workspace version

### Workflow Execution
- **Run Configurations**: Dedicated Dataform workflow run configuration, by actions, by tags, or the whole project
- **Gutter Icons**: Run an action from a SQLX file, or run all actions sharing its tags
- **Execution View**: Live progress per action, with status, timings and failure reasons
- **BigQuery Job Details**: Job id, bytes processed and billed, duration and child job queries for each executed action

### BigQuery Query Execution
- **Execute Query**: Run the compiled query of an action directly from the preview editor; when an
  edit is not compiled yet, it waits for the compilation so the query matches the sources
- **Results Grid**: Paged results shown in the Services view, with query statistics

### SQLX Formatting
- **Built-in Formatter**: Formatting model aware of injected SQL, JavaScript and config blocks
- **Sqlfluff Support**: Optional external formatting through Sqlfluff, configurable in the plugin settings

### Project Setup
- **Module Builder**: Create new Dataform projects with proper structure
- **CLI Integration**: Automatic Dataform CLI installation and setup
- **Project Indexing**: Fast indexing of Dataform core definitions and JavaScript symbols
- **Compilation**: Project compilation integrated with the IDE build; compilation errors and failing
  BigQuery dry-runs are reported in the Build tool window, grouped per file
- **Automatic Compilation**: The project is recompiled after edits once typing stops, on save, and
  after external changes such as a checkout, a pull or a rollback
- **New SQLX File**: `File` → `New` → `SQLX File`
- **Disabled Actions**: SQLX files declaring `disabled: true` are greyed out in the project view
- **Core Version Migration**: Suggests moving `dataformCoreVersion` from `workflow_settings.yaml` to
  `package.json` when a project is opened
- **Settings**: Under `Settings` → `Tools` → `Dataform`: Dataform core install path and one-click
  CLI & core installation, Sqlfluff executable, config and arguments, and toggles for expression
  folding, and inline compilation and BigQuery errors

## Installation

1. Download the plugin from the [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/30020)
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
