## 1. Scope the build workflow to pull requests

- [x] 1.1 Remove the `push: branches: [main]` trigger from `.github/workflows/build.yml`, leaving
      `pull_request` and `workflow_dispatch`
- [x] 1.2 Add `ref: ${{ github.event.pull_request.head.sha }}` to the checkout, keeping
      `fetch-depth: 0`, so the reviewed commits sit on the first-parent line the plugin reads
- [x] 1.3 Keep the `release-please--` head-ref guard — the release pull request still carries a
      changelog entry for the version being cut and no source change
- [x] 1.4 Keep the job named `build`; branch protection requires that context on every pull request
- [x] 1.5 Replace the checkout comment: the current one says the version derives from "tags and the
      changelog", which the manifest-mode change already made untrue

## 2. Move default-branch verification into the release workflow

- [x] 2.1 Add a verification job to `.github/workflows/release.yml` with `needs: release-please`,
      named something other than `build` so it can never be mistaken for the required check
- [x] 2.2 Give it the same steps the old main-branch build ran: `./gradlew check`, the dogfood build
      with isolated projects, and the released-versus-source version comparison
- [x] 2.3 Check out with `fetch-depth: 0`; no explicit `ref`, since the pushed commit is the history
      release-please just read
- [x] 2.4 Comment why the job lives here rather than in `build.yml` — cross-workflow `needs:` does not
      exist, so this is the only place the ordering can be written down
- [x] 2.5 Move the `Test Report` step across with it, keeping `if: ${{ !cancelled() }}`

## 3. Partition the release-cutting push

- [x] 3.1 Add `if: ${{ needs.release-please.outputs.release_created != 'true' }}` to the `fidelity`
      job
- [x] 3.2 Leave the empty-`title` early exit in place — an ordinary push with nothing releasable also
      has no pending pull request, and that is a genuine no-op
- [x] 3.3 Comment why: on a release commit the plugin returns the bare release version, so the
      job's `-SNAPSHOT` expectation could only fail wrongly
- [x] 3.4 Confirm `publish` keeps `release_created == 'true'`, so the two jobs are mutually exclusive
      by construction

## 4. Verify the wiring before merging

- [x] 4.1 Confirm `.github/settings.yml` still requires only `context: build`, and that no
      default-branch-only job name was added to `required_status_checks`
- [x] 4.2 Open the change as a pull request and confirm a check named `build` is reported on it —
      PR #6, run 30945780235, the only run on the branch
- [x] 4.3 Confirm the pull request build resolves a version that accounts for the commits under
      review — the checkout is now `HEAD is now at 1bcb000 ci: order default-branch verification`
      where it was `HEAD is now at 49c0a9e Merge 26a3d1a into 2157202`, so the reviewed commit is on
      the first-parent line. The dogfood comparison the task proposed cannot discriminate here: this
      change is `ci:`-scoped, so both refs yield `bump=NONE`. A releasable commit type will show it
      in the number
- [x] 4.4 After merge, confirm exactly one workflow checks out the main commit to verify it, and that
      `fidelity` and the verification job both run — `44243bc` produced only `release`, where
      `8423dd3` produced both `build` and `release`; `verify` started 10s after `release-please`

## 5. Confirm the release path on the next release

- [ ] 5.1 On the next release-cutting push, confirm the verification job starts after the tag exists
      and resolves the bare release version rather than failing on a missing tag
- [ ] 5.2 Confirm `fidelity` is skipped on that push and `publish` runs
- [ ] 5.3 Confirm the plugin pin in `settings.gradle` is bumped by a separate dependabot pull request
      after publication, not by the release commit — see design.md, Context
