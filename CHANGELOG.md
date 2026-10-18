# Changelog

## [3.0.0](https://github.com/joke/conventional-version/compare/v2.1.0...v3.0.0) (2026-10-18)


### ⚠ BREAKING CHANGES

* protected members used as testing seams become package-private and leave the inheritable API. Nothing documents these as extension points and the plugin is applied rather than extended, so no consuming build should notice.

### Build System

* align the build toolchain with the shared conventions ([751826d](https://github.com/joke/conventional-version/commit/751826d1a4cea45a9ef149f87b131c3f24364a42))

## [2.1.0](https://github.com/joke/conventional-version/compare/v2.0.0...v2.1.0) (2026-08-05)


### Features

* apply the plugin at project level as well as in settings ([38e62d5](https://github.com/joke/conventional-version/commit/38e62d58d07060603886fbda4132e72f9fb26d93))

## [2.0.0](https://github.com/joke/conventional-version/compare/v1.0.0...v2.0.0) (2026-08-04)


### ⚠ BREAKING CHANGES

* release-please must run in manifest mode. release-please-config.json and .release-please-manifest.json are required at the git repository root, and a missing file fails the build. CHANGELOG.md is no longer read. The `conventionalVersion { }` settings block is removed: initialVersion, bumpMinorPreMajor and bumpPatchForMinorPreMajor move to initial-version, bump-minor-pre-major and bump-patch-for-minor-pre-major in release-please-config.json, and tagPrefix has no counterpart because release-please has no tag-prefix option - its default corresponds to include-v-in-tag, which is enabled by default. Migration is behaviour-preserving: keeping release-type: simple reproduces the same tags, changelog and numbers. See the README's "Migrating from 1.x".

### Features

* read release-please's own configuration and version each package from it ([a803b0f](https://github.com/joke/conventional-version/commit/a803b0faaef502eb492d6d451bf89c29925acb57))

## 1.0.0 (2026-08-02)


### Features

* calculate versions from conventional commits ([5db67d4](https://github.com/joke/conventional-version/commit/5db67d49f3b4a5c3b7f67e1f69abdc8a299c2fdf))
