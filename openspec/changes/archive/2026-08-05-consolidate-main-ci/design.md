## Context

See proposal.md — Why. What shapes the approach is a set of constraints that are not obvious from
reading either workflow file:

**GitHub Actions has no cross-workflow `needs:`.** `build.yml` and `release.yml` both declare
`on: push: branches: [main]`, so they start together and race. `publish` only ever won that race by
accident of being in the same file as `release-please`. Any fix that keeps the two workflows separate
is a timing guess.

**The plugin reads `--first-parent`.** `GitRepository.LOG_WITH_PATHS` walks the first-parent line so a
merge reports the paths it brought in. A pull request build checks out `refs/pull/N/merge`, whose
first parent is the base branch — so the pull request's own commits sit on the second parent and
their conventional-commit types are never read. Observed directly in run 30902877620:
`HEAD is now at 49c0a9e Merge 26a3d1a into 2157202`.

**Merge settings make the merge ref fictional.** `settings.yml` sets `allow_rebase_merge` as the only
merge mode with `required_linear_history: true`, so `refs/pull/N/merge` is a shape that never reaches
`main`. `strict: true` additionally forces the branch current before merging, which is what makes the
pull request head a faithful stand-in for the post-merge history.

**Branch protection names a check.** `required_status_checks.checks` contains `context: build`. That
string is a *job* name, and it must keep being produced by something that runs on pull requests.

**The plugin pin cannot be bumped in the release commit.** `settings.gradle` applies the plugin from
the portal by version. If the release commit bumped that pin to the version being released, the
publish job would check out the tag, try to resolve a plugin version that is not published yet, and
fail — permanently, since the tag is immutable and the publish can never succeed. The bump must land
after publication. Dependabot's `gradle` ecosystem already does exactly this, in a separate pull
request; this is recorded so nobody later reaches for release-please `extra-files`.

## Goals / Non-Goals

**Goals:**

- Express the ordering that already implicitly exists, rather than tolerating a race.
- Make each job's checkout ref state what that job is verifying.
- Keep merges gated by a check that pull requests actually produce.

**Non-Goals:**

- Changing the plugin. `RepositoryStateReader.baseShaOf` throwing on a recorded release with no tag
  stays exactly as it is — it is the correct response to that state, and the alternative (treating a
  missing tag as "never released") publishes a wrong immutable coordinate.
- Deriving the base commit from the manifest's own git history instead of from a tag. That would make
  the plugin immune to unfetched tags for *every* consumer, not just this repository, and it is a
  larger question about the plugin's contract. Separate change.
- Running `check` on the release commit for its own sake. See Decisions.
- Reducing the number of workflow runs. The count stays at two; what changes is that they stop
  overlapping and stop duplicating work.

## Decisions

### Main-branch verification moves into `release.yml`, gated on `needs: release-please`

The race is not a timing problem to be padded around; it is an ordering that cannot be written down
while the jobs live in different files. Moving the default-branch `check` into `release.yml` and
giving it `needs: release-please` makes the tag's existence a precondition rather than a hope.

Alternatives considered:

- **Skip the release commit in `build.yml`** via `if: !startsWith(github.event.head_commit.message,
  'chore(main): release')`. One line, and it mirrors the existing `release-please--` head-ref guard.
  Rejected: it string-matches a commit message that release-please owns and can reformat, and it
  leaves the release commit unverified — which happens to be fine today only because the release
  commit touches no source, a fact nothing enforces.
- **Poll for the tag** with a retry loop around `git fetch --tags`. Rejected: it encodes "wait for
  another workflow" as a sleep, and the timeout is a guess that fails under load.
- **`workflow_run` trigger** on `build.yml`, chained after `release.yml`. Rejected: `workflow_run`
  runs against the default branch's workflow definition rather than the triggering commit's, so a
  workflow change could not be tested by the pull request that makes it.

Cost: main-branch verification starts roughly 10 seconds later, after `release-please` completes.

### `check` on the release commit is kept, but is not the reason for the change

The release commit differs from the last verified commit by `CHANGELOG.md` and
`.release-please-manifest.json` only (confirmed against the commit API for `0db9c23`), and neither is
on a compile or test path. Re-running the full `check` there is close to pure duplication.

It is kept anyway because once `check` is downstream of `release-please` it costs nothing extra to let
it run on every default-branch push, and a uniform rule ("main commits are verified") is easier to
keep true than a conditional one. The manifest value *is* new at that commit and *is* the plugin's
input, so the run is not entirely redundant — it is the only place that input is exercised against the
real repository before the coordinate becomes immutable.

Rejected: adding a separate version-versus-tag assertion to the publish job. Once `check` runs at that
commit under a guaranteed tag, the assertion checks something already established.

### `fidelity` is skipped via `needs.release-please.outputs.release_created`

On a release-cutting push the plugin returns the bare release version, because `headIsReleaseCommit`
is true. `fidelity` builds its expectation as `"${title##* }-SNAPSHOT"` unconditionally, so a pending
release pull request found at that moment would produce `expected=2.1.0-SNAPSHOT` against
`actual=2.0.0` — a false failure on a successful release. Today it is unreachable, because the only
new commit is a `chore` that opens no pull request; run 92052190506 took the empty path and printed
`No pending release pull request; nothing to compare.` after 16 seconds of checkout and setup.

Using `release_created` rather than a commit-message match reuses a signal release-please already
publishes, and partitions the two jobs by construction: a push either cuts a release or predicts one.

The existing empty-`title` early exit stays. An ordinary push with nothing releasable (a `docs:`
commit) also has no pending pull request, and that is a genuine no-op rather than a skippable one.

### `build.yml` becomes pull-request-only and checks out `github.event.pull_request.head.sha`

This is the one change that alters what is verified rather than when. Today a pull request build
resolves a version from a history in which the proposed commits are invisible, so a `feat!:` under
review contributes nothing to the number the build reports. Checking out the head makes the reviewed
commits first-parent, which for a plugin whose entire product is "predict the version from commit
history" is the difference between a real check and a decorative one.

The usual objection — "you are no longer testing the merge result" — is answered by `strict: true`
plus rebase-only merging: the head is already on top of current `main`, and the rebase that lands it
produces the same history.

The job keeps the name `build`, because branch protection requires that context. The default-branch
job in `release.yml` takes a different name and must not be added to
`required_status_checks`.

### Two workflows, not one

`build.yml` keeps pull requests; `release.yml` owns the default branch. Merging them into a single
file would mean one workflow with mutually exclusive halves and an `if:` on nearly every job.
Splitting by trigger keeps each file's jobs unconditionally relevant to the event that started it.

### `check` and `fidelity` stay separate jobs

They run in parallel after `release-please`. Merging them would save one Gradle setup, roughly 13
seconds, at the cost of failure attribution: `check` failing means the code is wrong, `fidelity`
failing means the plugin's prediction diverged from release-please. Those warrant different reactions
and should not share a red X.

## Risks / Trade-offs

- **A pull request that goes stale is verified against a history that will not land** → `strict: true`
  requires the branch to be current before merge, and GitHub re-runs the required check after an
  update. The window exists only for branches that cannot be merged yet.
- **Default-branch verification is delayed behind `release-please`** → roughly 10 seconds on a job
  that already takes over two minutes. Accepted.
- **A failure in `release-please` now blocks default-branch verification entirely** → previously the
  two were independent, so a release-tooling outage still produced a green build signal. That signal
  was the one that failed on 2.0.0, so its independence was not worth much; and the pull request
  build, which gates merges, is unaffected.
- **`release_created` is release-please's output and could change semantics across versions** → the
  same exposure the `publish` job already accepts, and the failure mode is a skipped or extra job
  rather than a wrong publication. The alternative, matching commit messages, is strictly more
  fragile.
- **Adding the new default-branch job to branch protection would deadlock merges** → called out in
  the spec and in the tasks; the check names are deliberately different so the mistake is visible.

## Migration Plan

The change is self-applying: the first push to `main` after merge exercises the new ordering, and the
first release after that exercises the release-cutting partition. There is no state to migrate.

Rollback is reverting the two workflow files. Nothing outside `.github/` changes, and no published
artifact depends on any of it.

The failed run on the 2.0.0 release commit (30927093000) is left as-is. It records a real historical
state and re-running it now would succeed only because the tag has since appeared, which proves
nothing.
