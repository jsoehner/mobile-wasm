# Contributing to MobileWasm

Thank you for your interest in contributing to MobileWasm! This project provides a cross-platform way to run WebAssembly modules on both Android and as a Progressive Web App (PWA).

## Code of Conduct
Please read and follow our [Code of Conduct](CODE_OF_CONDUCT.md).

## How to Contribute

### Reporting Bugs
1. **Check existing issues** - Search the issue tracker to see if the bug has already been reported.
2. **Create a new issue** - If not found, create a new issue using the bug report template.
3. **Provide details** - Include:
   - Version of MobileWasm
   - Operating system
   - Steps to reproduce
   - Expected vs actual behavior
   - Relevant logs or screenshots

### Suggesting Features
1. **Check existing requests** - Search issues for similar feature requests.
2. **Create a feature request** - Use the feature request template.
3. **Describe the use case** - Explain why this feature would be valuable to the mobile or web ecosystem.

### Contributing Code
1. **Fork the repository**
2. **Create a feature branch**: `git checkout -b feature/your-feature-name`
3. **Make your changes**
4. **Run tests**: 
   - Android: `./gradlew test`
   - PWA: `npm test`
5. **Commit your changes** with a descriptive message following Conventional Commits.
6. **Push to your fork**: `git push origin feature/your-feature-name`
7. **Create a Pull Request**

## Development Setup

### Prerequisites
- Android Studio, Android SDK, and NDK for Android.
- Node.js and npm for PWA.
- Git.

### Setup
1. **Clone the Repository**:
   ```bash
   git clone https://github.com/jsoehner/mobile-wasm.git
   cd mobile-wasm
   ```
2. **Install Dependencies**:
   - Android: `./gradlew assembleDebug`
   - PWA: `npm install`
3. **Run Tests**:
   - Android: `./gradlew test`
   - PWA: `npm test`

## Commit Message Guidelines
We use **Conventional Commits**. Please use the following prefixes:
- `feat:` A new feature
- `fix:` A bug fix
- `docs:` Documentation only changes
- `refactor:` A code change that neither fixes a bug nor adds a feature
- `perf:` A code change that improves performance
- `test:` Adding missing tests or correcting existing tests
- `chore:` Changes to the build process or auxiliary tools and libraries

Example: `feat(android): add support for WasmEdge 0.x`

## Pull Request Process
1. Ensure all tests pass.
2. Update documentation (if applicable).
3. Add tests for new functionality.
4. Fill out the PR template completely.
5. Request review from maintainers.

## Questions?
Feel free to open an issue for questions or join discussions in existing issues.
