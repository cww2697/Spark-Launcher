# Contributing to Spark Launcher

Thanks for your interest in contributing! This document explains how to set up your environment, propose changes, and
what we expect in contributions.

Important: Do not attach or submit code via uploaded files (e.g., .zip, .exe, .class, or source files) in issues or pull
requests. Any attached code files, zip archives, or executables will be deleted. No official code will be accepted in
this manner. All code contributions must come through pull requests from forks as described below.

## Getting started

- Before starting work, please search for existing issues and pull requests to avoid duplication.
- If your change is substantial, consider opening an issue first to discuss the approach.

## How to contribute

1. Fork the repository to your GitHub account.
2. Create a feature branch from the latest `main` (or the default) branch.
    - Suggested format: `feat/short-description`, `fix/issue-123`, or `docs/area`.
3. Make your changes in your fork and commit with clear messages.
4. Ensure the project builds and basic checks pass locally (see Build & run below).
5. Push your branch to your fork and open a Pull Request (PR) back to this repository’s default branch.
6. Your PR will be reviewed. Please be responsive to feedback and requested changes. PRs are merged only after review
   approval.

## Code submission policy

- Do not upload binaries or archives: any `.zip`, `.exe`, or other binary attachments will be removed.
- Do not attach source files directly to issues/PR discussions. Provide changes as commits in your branch.
- If you want to share example data or logs, redact sensitive information and paste text directly or link to a gist.

## Development setup

This project uses Gradle and Kotlin. Typical workflow:

- Clone your fork:
    - `git clone https://github.com/<your-username>/SparkLauncher.git`
    - `cd SparkLauncher`
- Sync dependencies and build:
    - On Windows: run `gradlew.bat build`
    - On macOS/Linux: run `./gradlew build`

If tests are present, run them with `gradlew test` (or `gradlew.bat test` on Windows).

## Commit messages

- Use clear, descriptive commit messages.
- Reference issues when appropriate, e.g., `Fix #123: handle null game index`.
- Keep commits focused; prefer smaller, logical commits over large unrelated ones.

## Pull request checklist

Before opening or requesting review:

- The project builds successfully.
- New code follows existing style and conventions.
- Tests updated or added when relevant.
- Documentation or README adjusted if behavior changes.
- PR description explains the change, rationale, and any user-facing impact.

## Reviews and merging

- Maintainers review all PRs before merging.
- Requested changes should be addressed via additional commits (squashing may be requested before merge).
- CI must pass before merge (if configured).

## Reporting bugs and requesting features

- Use the GitHub Issues page and select the appropriate template.
- Include clear reproduction steps, expected vs. actual behavior, and environment details.

## Community standards

- Be respectful and constructive.
- Follow the project license and third-party licenses.

Thank you for contributing to Spark Launcher!