## Purpose

Defines how this repository's automation is triggered, ordered and scoped: which commit each job
verifies, what must have completed before a job resolves a version, and which jobs run on a push that
cuts a release as opposed to one that only predicts the next.

## ADDED Requirements

### Requirement: Release tooling precedes version resolution on the default branch

No job that resolves this project's version from a default-branch commit SHALL start before the
release tooling has finished creating any tag that commit's recorded release names. The plugin
locates a recorded release by its tag and fails when the tag is absent, so a job that reads a release
commit before the tag exists reports a failure that describes the repository's transient state rather
than any defect.

#### Scenario: The release commit is verified with its tag present

- **WHEN** the commit that records a new release is pushed to the default branch
- **THEN** verification of that commit begins only after the release tag has been created, and the
  version resolves to the bare release version the tag names

#### Scenario: Ordering is declared, not inferred from the commit

- **WHEN** the automation decides whether a default-branch commit is a release commit
- **THEN** it uses the release tooling's own reported outcome, and not the commit message, the branch
  name or the changed paths

#### Scenario: An ordinary push is unaffected

- **WHEN** a commit that records no release is pushed to the default branch
- **THEN** verification runs and the version resolves to a snapshot of the next predicted release

### Requirement: A default-branch push either predicts a release or publishes one

Every push to the default branch SHALL run exactly one of the release-prediction check and the
publication, never both and never neither-when-one-applies. On a push that cuts a release the
project's version is the bare release version, which cannot equal the snapshot a prediction check
compares against, so running the prediction there can only pass vacuously or fail wrongly.

#### Scenario: Prediction is skipped on a release-cutting push

- **WHEN** a push to the default branch causes the release tooling to create a release
- **THEN** the release-prediction check does not run, and the publication does

#### Scenario: Publication is skipped on an ordinary push

- **WHEN** a push to the default branch creates no release
- **THEN** the publication does not run, and the release-prediction check does

#### Scenario: Prediction with nothing pending is not a failure

- **WHEN** the release-prediction check runs and the release tooling proposes no release
- **THEN** the check succeeds, having nothing to compare

### Requirement: Publication verifies the commit the tag names

The publication SHALL resolve its version from the commit the release tag points at, without a
version override, so that the coordinate published is the one the project calculates for itself. A
supplied version that agreed would add nothing, and one that disagreed would conceal the
disagreement.

#### Scenario: The publication checks out the tag

- **WHEN** a release is published
- **THEN** the working tree is the commit the release tag names, with the full history and tags
  available

#### Scenario: No version is supplied to the publication

- **WHEN** the publication runs
- **THEN** no version property is passed to it, and the version it publishes is the one the plugin
  calculates

### Requirement: Pull request verification observes the commits under review

Verification of a pull request SHALL run against the head of the pull request rather than a synthetic
merge of it into the base. The version calculation reads only the first-parent line, on which a
synthetic merge places the base branch — so the commits under review, and the release-relevant types
they carry, would not be observed at all.

#### Scenario: The reviewed commits are on the first-parent line

- **WHEN** a pull request is verified
- **THEN** the commits proposed by the pull request appear in the history the version calculation
  reads, and their conventional-commit types contribute to the calculated version

#### Scenario: The verified history is the history that will land

- **WHEN** the default branch requires a pull request to be current before merging, and the
  repository merges by rebase
- **THEN** the history verified on the pull request is the history the merge produces

#### Scenario: Release-tooling pull requests are not verified

- **WHEN** the release tooling opens or updates its release pull request
- **THEN** that pull request is not verified, because it carries a changelog entry for the version
  being cut and no source change

### Requirement: Verification is not duplicated across workflows

A single default-branch commit SHALL NOT be verified by more than one workflow. Resolving the same
commit's version in two places costs a second checkout and toolchain setup to reach an answer already
known, and leaves the two results able to disagree with no defined precedence.

#### Scenario: One workflow verifies a default-branch commit

- **WHEN** a commit is pushed to the default branch
- **THEN** exactly one workflow checks out that commit to verify it

#### Scenario: Pull request and default-branch verification remain distinct

- **WHEN** a pull request is merged
- **THEN** the pull request verification and the default-branch verification both run, because they
  observe different histories, and neither is a substitute for the other

### Requirement: The merge-gating status check keeps its name

The status check that branch protection requires SHALL be produced by a job that runs on every pull
request. A check named in branch protection but produced only on the default branch would never
appear on a pull request and would block every merge indefinitely.

#### Scenario: The required check is produced on a pull request

- **WHEN** a pull request is opened
- **THEN** a status check with the name branch protection requires is reported for it

#### Scenario: Default-branch jobs are not required checks

- **WHEN** branch protection is inspected
- **THEN** it requires no check that only a default-branch push can produce

### Requirement: Verification reads the full history

Every job that resolves this project's version SHALL check out the complete history and tags. The
version derives from the release manifest and the tag that locates the recorded release, so a shallow
or tagless checkout yields a wrong coordinate rather than an absent one.

#### Scenario: Full history is requested

- **WHEN** a job that resolves a version checks out the repository
- **THEN** it requests the full history rather than a shallow clone

#### Scenario: A shallow checkout fails rather than guesses

- **WHEN** a job resolves a version from a shallow checkout
- **THEN** the build fails and names the checkout as the cause
