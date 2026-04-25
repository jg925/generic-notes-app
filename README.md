# generic-notes-app

## Overview

`generic-notes-app` is an open-source, generic notes application that serves as the **reference implementation counterpart** to the [`hwdn-format-spec`](https://github.com/jg925/hwdn-format-spec) repository.

Together, these two repositories define and demonstrate the **HWDN file format** — an open, portable format for storing and exchanging notes data.

## Purpose

This repository exists to:

- **Implement** the HWDN file format as defined in `hwdn-format-spec`, providing a working, real-world application that reads and writes `.hwdn` files.
- **Validate** the format specification by serving as a living proof-of-concept — if this app can't do something cleanly, the spec needs revisiting.
- **Demonstrate** how any notes application can adopt the HWDN format, acting as a reference for third-party developers who want to build HWDN-compatible tools.
- **Drive iteration** on the open format spec through practical usage and discovered edge cases.

## Relationship to `hwdn-format-spec`

| Repo | Role |
|---|---|
| [`hwdn-format-spec`](https://github.com/jg925/hwdn-format-spec) | Defines the HWDN file format: schema, rules, versioning, and compliance requirements |
| `generic-notes-app` *(this repo)* | Implements the HWDN format in a generic notes application; validates and exercises the spec |

Changes to the format spec should be reflected here, and friction discovered in this app should inform updates to the spec.

## HWDN File Format

The HWDN format is an open-source file format designed to represent notes in a portable, application-agnostic way. Full format documentation, schema definitions, and versioning history live in the [`hwdn-format-spec`](https://github.com/jg925/hwdn-format-spec) repository.

## Contributing

Contributions are welcome! If you find an issue with how this app interprets the HWDN spec, please open an issue here *and* consider whether the spec itself needs clarification — then open a corresponding issue or PR in [`hwdn-format-spec`](https://github.com/jg925/hwdn-format-spec).

## License

This project is open source. See [LICENSE](./LICENSE) for details.
