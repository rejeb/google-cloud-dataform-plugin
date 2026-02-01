# Google Cloud Dataform Plugin for IntelliJ IDEA

A comprehensive IntelliJ IDEA plugin that provides advanced language support for Google Cloud Dataform projects, enabling developers to work efficiently with SQLX files and Dataform workflows.

![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)
![Platform](https://img.shields.io/badge/platform-IntelliJ-lightgrey.svg)
![version](https://img.shields.io/badge/version-0.1.0-green.svg)
[![Scala](https://img.shields.io/badge/Java-21-blue)](https://www.java.com/fr/)

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

### Project Setup
- **Module Builder**: Create new Dataform projects with proper structure
- **CLI Integration**: Automatic Dataform CLI installation and setup
- **Project Indexing**: Fast indexing of Dataform core definitions and JavaScript symbols

## Installation

1. Download the plugin from the [JetBrains Marketplace](https://plugins.jetbrains.com/) (once published)
2. Install via IntelliJ IDEA: `Settings` → `Plugins` → `Marketplace` → Search for "Google Cloud Dataform"
3. Restart IntelliJ IDEA

## Requirements

- IntelliJ IDEA 2025.3.2 or later
- Java 21 or later
- Node.js (for Dataform CLI)

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
