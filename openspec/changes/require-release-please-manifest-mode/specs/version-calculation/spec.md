## MODIFIED Requirements

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

## ADDED Requirements

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
