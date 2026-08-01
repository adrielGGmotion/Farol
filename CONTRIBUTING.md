# Contributing to Farol

Thanks for your interest in contributing! This project is a fork of
[Stario](https://github.com/albu-razvan/Stario) and is maintained as a simple,
focused launcher project.

## Getting started

### Building

The easiest way to build is with the provided Docker image:

```bash
docker build --platform linux/amd64 -t stario-dev .
docker run --platform linux/amd64 --rm -it \
  -v "$(pwd)/build":/usr/local/stario/build \
  stario-dev
```

Inside the container, run:

```bash
./build.sh
```

This produces a debug APK, an unsigned release APK, and (when signing
credentials are provided) a signed AAB and APK under `build/v<version>/`.

See the [README](./README.md) for more details.

### Running

- Requires **Android SDK 29+** (Android 10.0 or later).
- Build and install with your IDE, or use `./gradlew assembleDebug` and
  install the resulting `app/build/outputs/apk/debug/app-debug.apk`.

## Reporting issues

- **Bugs**: open a [Bug Report](../../issues/new?template=bug-report.md) and
  include your device, OS version, and steps to reproduce.
- **Security vulnerabilities**: do **not** open a public issue. Report them
  privately — see [SECURITY.md](./SECURITY.md).

## Pull requests

- Keep PRs **small and focused**. One logical change per PR makes review fast
  and keeps the project easy to maintain.
- Before opening a PR, make sure the **Build** workflow passes:
  ```bash
  ./gradlew build
  ```
- Update the PR description with a short summary of what changed and why.
- The maintainer may ask for changes; inline review comments are preferred
  over large follow-up PRs.

## Code style

- The codebase is primarily **Java** with some **Kotlin** (Compose).
- Match the existing style of the file you are editing. There is no
  auto-formatter enforced in CI, so keep diffs tidy and readable.

## Versioning

Releases are tagged as `v<version>`, matching the `versionName` in
`app/build.gradle` (e.g. `v2.16`). A
**Release** workflow builds signed artifacts and creates a draft GitHub
Release from the tag — the maintainer publishes it after review.
