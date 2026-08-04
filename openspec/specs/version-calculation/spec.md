# version-calculation Specification

## Purpose

Derives the version a project should carry from its conventional commit history, producing the same
number `release-please` will publish, plus a usable Maven snapshot coordinate for every commit in
between.

## Requirements

### Requirement: Base version resolution

The system SHALL determine the last released version of each package from the release manifest
`release-please` maintains in the repository working tree, and SHALL determine the commit that
version was released at from the matching git tag for that package. A tag whose version has no
corresponding entry in the manifest SHALL NOT be treated as a release.

#### Scenario: Released version taken from the release manifest

- **WHEN** the manifest records `1.3.0` for a package and a tag `v1.3.0` exists
- **THEN** the base version is `1.3.0` and the range start is the commit that `v1.3.0` points at

#### Scenario: Hand-created tag is ignored

- **WHEN** the most recent recorded release is `1.3.0` but tags `v5.5.5`, `v5.5.6` and `v5.5.7` also
  exist on later commits with no corresponding manifest entry
- **THEN** the base version is `1.3.0`, not `5.5.7`

#### Scenario: A changelog does not record a release

- **WHEN** a `CHANGELOG.md` records a release of `1.3.0` that the manifest does not
- **THEN** the package is treated as never released, because the manifest is the record

#### Scenario: Release record exists but its tag does not

- **WHEN** a release of `1.3.0` is recorded but no tag matching it can be found
- **THEN** the build fails with a message naming the package, the version and the tag it looked for

#### Scenario: Package tag carries its component

- **WHEN** the manifest records `1.3.0` for a package whose component is `a`
- **THEN** the tag looked for is `a-v1.3.0`

#### Scenario: Package has never released

- **WHEN** no release is recorded for a package
- **THEN** the calculation for that package proceeds with no base version and the configured initial
  version is used

### Requirement: Commit range selection

For each package, the system SHALL analyse every commit reachable from `HEAD` that is not reachable
from that package's base release commit and that touched at least one path the package claims. When
a package has no base release commit, it SHALL analyse the entire history reachable from `HEAD`,
filtered by the same path claim.

#### Scenario: Commits since the last release

- **WHEN** a package's base release commit is `abc1234` and four commits touching its paths have
  landed on top of it
- **THEN** exactly those four commits are analysed for that package

#### Scenario: HEAD is the base release commit

- **WHEN** `HEAD` is the commit a package's base version was released at
- **THEN** no commits are analysed for that package

#### Scenario: Commits are attributed by the paths they touch

- **WHEN** the range contains one commit touching only `lib/a` and one touching only `lib/b`, and
  packages are declared at both paths
- **THEN** the first is analysed for `lib/a` only and the second for `lib/b` only

#### Scenario: A commit touching several packages is analysed for each

- **WHEN** one commit touches files under both `lib/a` and `lib/b`
- **THEN** it is analysed for both packages

#### Scenario: Packages have independent ranges

- **WHEN** two packages were last released at different commits
- **THEN** each package's range starts at its own base release commit

#### Scenario: Merge commits are traversed on the first-parent line only

- **WHEN** the range contains a merge commit whose second parent introduces commits
- **THEN** only the merge commit itself is analysed, not the commits it merged in

### Requirement: Conventional commit parsing

The system SHALL parse each commit message according to the Conventional Commits specification,
extracting the type, the optional scope, and whether the commit is breaking. A commit is breaking
when a `!` precedes the `:` in the header, or when the message body contains a `BREAKING CHANGE:` or
`BREAKING-CHANGE:` footer. A commit message that does not match the specification SHALL be ignored
rather than causing a failure.

#### Scenario: Type and description

- **WHEN** a commit message is `feat: add codec`
- **THEN** the type is `feat`, there is no scope, and the commit is not breaking

#### Scenario: Scope

- **WHEN** a commit message is `fix(codec): handle EOF`
- **THEN** the type is `fix`, the scope is `codec`, and the commit is not breaking

#### Scenario: Breaking marked by exclamation

- **WHEN** a commit message is `feat(codec)!: replace stream API`
- **THEN** the commit is breaking

#### Scenario: Breaking marked by footer

- **WHEN** a commit message header is `feat: replace stream API` and its body contains a
  `BREAKING CHANGE: the old API is gone` footer
- **THEN** the commit is breaking

#### Scenario: Hyphenated breaking footer

- **WHEN** a commit body contains a `BREAKING-CHANGE: the old API is gone` footer
- **THEN** the commit is breaking

#### Scenario: Non-conventional message

- **WHEN** a commit message is `save2`
- **THEN** the commit contributes nothing and no error is raised

#### Scenario: Multi-line message attribution

- **WHEN** two consecutive commits each have multi-line bodies and only the first contains a
  `BREAKING CHANGE:` footer
- **THEN** only the first commit is breaking

### Requirement: Bump reduction

The system SHALL reduce the analysed commits to a single bump type, taking the highest bump any
commit implies, where `major` outranks `minor`, which outranks `patch`, which outranks `none`.

A commit implies `major` when it is breaking, and `minor` when its type is `feat`. A commit whose
type is one of the remaining types that `release-please` renders into a visible changelog section —
`fix`, `perf`, `revert` and `deps` — implies `patch`. Every other type, including types that
`release-please` renders as hidden and types not defined by the Conventional Commits specification,
implies `none`.

#### Scenario: Feature outranks fix

- **WHEN** the range contains one `fix` and one `feat`
- **THEN** the bump is `minor`

#### Scenario: Breaking outranks everything

- **WHEN** the range contains several `feat` commits and one `fix!` commit
- **THEN** the bump is `major`

#### Scenario: Performance improvement bumps patch

- **WHEN** the range contains only a `perf: shrink the parse buffer` commit
- **THEN** the bump is `patch`

#### Scenario: Revert bumps patch

- **WHEN** the range contains only a `revert: restore the previous codec` commit
- **THEN** the bump is `patch`

#### Scenario: Dependency update bumps patch

- **WHEN** the range contains only a `deps: bump the parser` commit
- **THEN** the bump is `patch`

#### Scenario: Housekeeping types do not bump

- **WHEN** the range contains only `chore`, `docs`, `style`, `test`, `ci` and `refactor` commits
- **THEN** the bump is `none`

#### Scenario: Unknown type does not bump

- **WHEN** the range contains only a `bug: test1` commit
- **THEN** the bump is `none`

#### Scenario: Empty range

- **WHEN** no commits are analysed
- **THEN** the bump is `none`

#### Scenario: The release-please release commit is inert

- **WHEN** the range contains a `chore(main): release 1.3.0` commit authored by release-please
- **THEN** it contributes no bump

### Requirement: Explicit version override

When a commit in the analysed range carries a `Release-As:` footer naming a version, the system SHALL
use that version instead of applying any bump. The footer SHALL be recognised regardless of case, and
when several commits carry one, the most recent SHALL win.

#### Scenario: Override replaces the calculated bump

- **WHEN** the base version is `1.3.0`, the range contains a `feat:` commit, and one commit body
  carries a `Release-As: 2.0.0` footer
- **THEN** the version is `2.0.0-SNAPSHOT` rather than `1.4.0-SNAPSHOT`

#### Scenario: Most recent override wins

- **WHEN** two commits in the range carry `Release-As:` footers naming `2.0.0` and then `3.0.0`
- **THEN** the version is `3.0.0-SNAPSHOT`

#### Scenario: Override is case-insensitive

- **WHEN** a commit body carries a `release-as: 2.0.0` footer
- **THEN** the version is `2.0.0-SNAPSHOT`

#### Scenario: Override makes the range releasable

- **WHEN** the range contains only `chore:` commits and one of them carries a `Release-As: 2.0.0`
  footer
- **THEN** the version is `2.0.0-SNAPSHOT` and the range is reported as releasable

#### Scenario: Override applies with no recorded release

- **WHEN** the project has never released and a commit carries a `Release-As: 0.5.0` footer
- **THEN** the version is `0.5.0-SNAPSHOT` rather than the configured initial version

#### Scenario: Override does not apply on a release commit

- **WHEN** `HEAD` is the commit of the recorded release
- **THEN** the version is the bare recorded version, regardless of any `Release-As:` footer in
  history

### Requirement: Bump application

The system SHALL apply the reduced bump to the base version: a `major` bump increments the major and
zeroes the minor and patch, a `minor` bump increments the minor and zeroes the patch, and a `patch`
bump increments the patch. When the base major version is `0`, the system SHALL apply the pre-major
policy described below.

#### Scenario: Major bump

- **WHEN** the base version is `1.3.2` and the bump is `major`
- **THEN** the calculated version is `2.0.0`

#### Scenario: Minor bump

- **WHEN** the base version is `1.3.2` and the bump is `minor`
- **THEN** the calculated version is `1.4.0`

#### Scenario: Patch bump

- **WHEN** the base version is `1.3.2` and the bump is `patch`
- **THEN** the calculated version is `1.3.3`

#### Scenario: Pre-major breaking change bumps major by default

- **WHEN** the base version is `0.3.1`, the bump is `major`, and the pre-major minor policy is
  disabled
- **THEN** the calculated version is `1.0.0`

#### Scenario: Pre-major breaking change bumps minor when configured

- **WHEN** the base version is `0.3.1`, the bump is `major`, and the pre-major minor policy is
  enabled
- **THEN** the calculated version is `0.4.0`

#### Scenario: Pre-major feature bumps patch when configured

- **WHEN** the base version is `0.3.1`, the bump is `minor`, and the pre-major patch policy is
  enabled
- **THEN** the calculated version is `0.3.2`

### Requirement: Version formatting

The system SHALL produce a bare version when `HEAD` is the commit the base version was released at,
and a `-SNAPSHOT` version otherwise. The version SHALL contain no commit hash, no commit count and no
semver build metadata, so that every commit between two releases resolves to the identical
coordinate.

#### Scenario: On the release commit

- **WHEN** `HEAD` is the commit tagged for the recorded release `1.3.0`
- **THEN** the version is exactly `1.3.0`, with no suffix and no derivation applied

#### Scenario: Off the release commit

- **WHEN** the base version is `1.3.0` and the bump is `minor`
- **THEN** the version is `1.4.0-SNAPSHOT`

#### Scenario: Stable across the range

- **WHEN** two different commits between the same pair of releases are built and both reduce to the
  same bump
- **THEN** both produce the identical version string

#### Scenario: No releasable commits floors at patch

- **WHEN** the base version is `1.3.0` and the bump is `none`
- **THEN** the version is `1.3.1-SNAPSHOT`

#### Scenario: Never released

- **WHEN** the project has no recorded release and the configured initial version is `1.0.0`
- **THEN** the version is `1.0.0-SNAPSHOT` regardless of what the analysed commits imply

#### Scenario: Dirty working tree does not alter the version

- **WHEN** the working tree has uncommitted modifications
- **THEN** the version is the same as it would be with a clean tree

### Requirement: Releasability signal

The system SHALL expose whether the analysed commits warrant a release, so that publishing can be
gated on it independently of the version string.

#### Scenario: Releasable range

- **WHEN** the bump is `major`, `minor` or `patch`
- **THEN** the range is reported as releasable

#### Scenario: Non-releasable range

- **WHEN** the bump is `none`
- **THEN** the range is reported as not releasable, even though a `-SNAPSHOT` version was still
  produced

#### Scenario: On a release commit

- **WHEN** `HEAD` is the commit of the recorded release
- **THEN** the range is reported as not releasable

### Requirement: Fidelity to release-please

The calculated version of each package SHALL equal the version `release-please` would publish for the
same history under the same configuration. The system SHALL take every option that affects a
calculated number from `release-please`'s own configuration rather than from a separate declaration,
so that the two cannot diverge. Where the system does not implement a behaviour that would change a
calculated number, it SHALL fail rather than produce a number.

#### Scenario: Options are not restated

- **WHEN** the initial version, pre-major minor policy or pre-major patch policy is set
- **THEN** it is set once, in `release-please`'s configuration, and the build offers no second place
  to set it

#### Scenario: Agreement with a pending release

- **WHEN** release-please has an open release pull request proposing version `1.4.0` for a package
- **THEN** the calculated version for that package is `1.4.0-SNAPSHOT`

#### Scenario: Unimplemented behaviour fails rather than guesses

- **WHEN** the configuration requests a behaviour that would change a calculated number and the
  system does not implement it
- **THEN** the build fails naming that behaviour, and no version is produced

#### Scenario: Dependency relationships between projects are not followed

- **WHEN** a commit touches only a path claimed by no package, and a released package's build depends
  on the code at that path
- **THEN** the released package's bump is `none`, because `release-please` attributes commits by path
  and does not follow build dependencies

### Requirement: Repository preconditions

The system SHALL fail with an actionable message, rather than falling back to a default version, when
the repository state makes a correct calculation impossible.

#### Scenario: Git is unavailable

- **WHEN** no `git` executable can be found
- **THEN** the build fails with a message stating that git is required

#### Scenario: Not a git repository

- **WHEN** the build runs in a directory that is not inside a git repository
- **THEN** the build fails with a message stating that a git repository is required

#### Scenario: Shallow clone

- **WHEN** the repository is a shallow clone
- **THEN** the build fails with a message stating that full history is required and naming the
  setting that provides it

#### Scenario: Failure is not silent

- **WHEN** any repository precondition is unmet
- **THEN** no version is produced and the initial version is not used as a fallback

### Requirement: Paths claimed by no package

Paths that no package claims are not releasable. The system SHALL treat work on such paths as
contributing to no package's bump, and SHALL report a constant, non-releasable version for them
rather than a version derived from any package.

#### Scenario: Constant version for unclaimed paths

- **WHEN** a version is reported for a path that no package claims
- **THEN** it is exactly `0.0.0-SNAPSHOT`

#### Scenario: The constant does not move when a package releases

- **WHEN** a package releases and its version changes
- **THEN** the version reported for unclaimed paths is still `0.0.0-SNAPSHOT`

#### Scenario: Unclaimed paths are never releasable

- **WHEN** commits in the range touch only paths that no package claims
- **THEN** the bump reported for those paths is `none` and they are reported as not releasable

#### Scenario: Work on unclaimed paths bumps nothing

- **WHEN** the only commit in the range is a `fix:` touching a path no package claims
- **THEN** no package's bump is raised by it

### Requirement: Linked version groups

When the configuration declares a group of components whose versions are linked, the system SHALL
calculate each member independently and then assign every member of the group the highest version
calculated for any member. Every member of the group SHALL be reported as releasable when any member
is, including a member whose own commits imply no bump.

#### Scenario: The highest version wins

- **WHEN** a group's members calculate to `1.4.0` and `1.3.3`
- **THEN** every member of the group is `1.4.0-SNAPSHOT`

#### Scenario: A member with no changes joins the release

- **WHEN** a group has three members and only one has commits in its range
- **THEN** all three carry the same version and all three are reported as releasable

#### Scenario: A package outside the group is unaffected

- **WHEN** a package is not named in any group and its own range implies a patch bump
- **THEN** its version is derived from its own commits alone

#### Scenario: Groups do not lower a version

- **WHEN** a member's own calculation already yields the highest version in its group
- **THEN** that member keeps its version and the other members are raised to it
