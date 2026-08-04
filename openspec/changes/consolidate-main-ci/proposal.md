## Why

Two workflows trigger independently on every push to `main`, so nothing can express an order between
them. The 2.0.0 release exposed what that costs: `build` checked out the release commit 11 seconds
before `release-please` created `v2.0.0`, the plugin found a manifest recording a release with no tag
to locate it, and the build failed on the one commit whose version is least in doubt. The release
itself succeeded, so the red build was pure noise — but the same race will recur on every release,
deterministically.

The same missing order hides three more defects: `fidelity` runs on release-cutting pushes where it
can only no-op, pull request builds verify a commit shape that never reaches `main`, and the two
workflows each pay a full checkout and Gradle setup to resolve the same version of the same commit.

## What Changes

- Main-branch verification moves into the release workflow, downstream of `release-please`, so the
  tag exists before any job resolves a version. The release-commit race disappears without a
  commit-message guard.
- `fidelity` is skipped when `release-please` reports `release_created`, partitioning every main push
  into exactly one of *predict the next release* or *publish this one*.
- The build workflow becomes pull-request-only and checks out the pull request head rather than
  `refs/pull/N/merge`, so the commits under review appear on the first-parent line the plugin reads.
- The redundant checkout and Gradle setup collapse: one workflow resolves the version of a main
  commit, not two.
- **BREAKING** for repository configuration: branch protection requires a status check named `build`.
  The pull-request job keeps that name, so protection continues to work, but the main-branch job is
  named separately and must not be added as a required check — no pull request would ever produce it.

## Capabilities

### New Capabilities

- `continuous-integration`: How this repository's workflows are triggered, ordered and scoped — which
  ref each job verifies, what must complete before a version is resolved, and which jobs run on a
  release-cutting push versus an ordinary one.

### Modified Capabilities

<!-- None. The verification stack, publication and self-versioning requirements in build-foundation
     describe what is verified and published; this change moves where and when those run, and
     introduces no new build behaviour. -->

## Impact

- `.github/workflows/build.yml` — loses the `push: main` trigger and the `release-please--` head-ref
  guard; gains an explicit checkout ref.
- `.github/workflows/release.yml` — gains the main-branch verification jobs and a skip condition on
  `fidelity`.
- `.github/settings.yml` — the required status check context stays `build`; recorded here because the
  job naming is load-bearing for merges, not because the file changes.
- No plugin source changes. The tag lookup in `RepositoryStateReader.baseShaOf` keeps throwing when a
  recorded release has no tag; this change stops CI from producing that state, rather than teaching
  the plugin to tolerate it.
