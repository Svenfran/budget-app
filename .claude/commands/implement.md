
---
description: Implement an approved plan identified by Jira key, folder name or path
argument-hint: "[DIV-123 | plan-name | plan-path]"
disable-model-invocation: true
---

# Implement — Divvy

Implement the approved plan identified by:

$ARGUMENTS

## 0. Git branch management

Before modifying any files, ensure the implementation runs
on the correct Git branch.

### Jira-based implementation

If the implementation target is a Jira issue key
matching `DIV-[0-9]+` (case-insensitive):

1. Normalize the issue key to uppercase.
2. Generate a short, descriptive kebab-case slug from the
   Jira issue summary:
    - Use lowercase ASCII letters, numbers and hyphens.
    - Remove special characters and replace spaces with hyphens.
    - Transliterate German umlauts (ä → ae, ö → oe, ü → ue, ß → ss).
    - Remove unnecessary filler words.
    - Keep the slug concise, preferably 2–5 meaningful words.
    - Do not invent information not present in the issue summary.
3. Use the branch name `feature/<ISSUE-KEY>-<slug>`. 
   Example: `feature/DIV-123-shopping-list-sorting`.
4. Check the current Git branch and working tree status.
5. Check whether the target branch exists locally.
6. If it does not exist locally, check whether it exists on
   the remote.
7. If the branch already exists:
    - Stay on it if it is already checked out.
    - Otherwise switch to the existing branch.
    - If it exists only on the remote, create a local tracking
      branch without overwriting remote history.
8. If the branch does not exist:
    - Create it from the configured base branch.
    - Prefer `develop` if available; otherwise use the
      repository's configured default branch.
    - Do not automatically pull, merge or rebase.
9. Confirm the active branch before starting implementation.

### Working tree safety

- Never discard, overwrite or stash uncommitted changes
  without explicit user approval.
- If switching branches could interfere with existing changes,
  stop and ask the user how to proceed.
- Do not force-checkout branches.
- Do not reset existing branches.
- Do not delete or recreate existing branches.
- Do not automatically push branches.
- Do not create commits unless explicitly requested.

### Monorepo handling

- Perform branch management from the `budget-app`
  monorepo root.
- Verify that the directory is a Git repository.
- If frontend and backend are separate Git repositories,
  do not assume a shared branch.
- Report the repository structure and request clarification
  before creating branches in multiple repositories.

### Free-text implementation

If the implementation target is not a Jira issue:

- Do not automatically invent a feature branch name.
- Use the current branch unless the user explicitly
  requests a new branch.
- Never implement directly on a protected branch
  such as `main` or `master` without confirmation.


## 1. Resolve implementation plan

Work from the budget-app monorepo root.

Resolve the argument as follows:

### Jira issue key

If the input matches `DIV-[0-9]+` (case-insensitive):

1. Normalize the key to uppercase.
2. Resolve the plan path:

`docs/plans/<ISSUE-KEY>/implementierungs-plan.md`

Example:

`/implement DIV-1`

resolves to:

`docs/plans/DIV-1/implementierungs-plan.md`

### Plan name

If the input is a short name without path separators:

`docs/plans/<name>/implementierungs-plan.md`

### Explicit path

If the input is a Markdown file path, use that exact path.

Do not search for unrelated plans or silently substitute another plan.

If the file does not exist, stop and tell the user to run `/plan` first.

## 2. Preconditions

- Read the complete plan.
- Read the root and relevant sub-project CLAUDE.md files.
- Confirm the user explicitly approved the current version of the plan after reviewing any Codex plan findings.
- Check for unresolved blocking questions.
- Inspect the current Git working tree.
- Preserve unrelated existing changes.

Do not infer approval solely from the existence of a plan file, `codex-review-1.md`, or a positive Codex verdict.

If the plan is still a draft and approval is unclear, ask before implementing.

## 3. Validate the plan

Compare the plan with the current codebase.

Verify:
- Referenced files still exist
- Existing architecture is accurately represented
- Planned API contracts are compatible
- Frontend and backend changes are coordinated
- No significant implementation assumptions have become invalid

For Jira-based plans, use the requirements and acceptance criteria stored in the plan.

Live Jira access is optional during implementation.

If Jira is available and requirements have materially changed, inform the user and request approval before expanding scope.

Do not silently modify the approved requirements.

## 4. Implementation principles

Follow these priorities:

1. Correctness and data integrity
2. Existing Divvy patterns
3. Minimal, focused changes
4. Readability and maintainability
5. Performance and efficiency

Reuse existing implementations before introducing new abstractions.

Avoid unrelated refactorings.

Classify changes as:
- Required: implement
- Local improvement: implement only when justified
- Out of scope: suggest separately

Ask for approval before significant deviations.

## 5. Phased implementation — mandatory user approval after every phase

**Hard stop rule:** Implement **exactly one pending phase per approval cycle**. After that phase, stop all implementation work, present the changes and verification, and wait for an explicit user message approving that specific phase. Never continue automatically, even if tests pass, the plan was approved, or the user originally asked to implement the entire plan. Do not interpret silence or lack of objections as approval.

### 5.1 Initialize or resume

1. Read the numbered phases in the approved plan. Do not silently regroup, merge, or reorder approved phases.
2. Use `docs/plans/<IDENTIFIER>/implementation-progress.md` as a **status log** (create it when starting implementation). Record plan path, a SHA-256 digest of the approved plan when tooling permits, branch/repository context, and each phase as `NOT_STARTED`, `IN_PROGRESS`, `AWAITING_APPROVAL`, `APPROVED`, or `BLOCKED`.
3. The progress log is **not proof of approval by itself**. On a new session, verify approval against available user conversation or ask the user to confirm the last approved phase. Never infer approval from a status file alone.
4. If the plan changed since the recorded digest, stop and request approval of the revised plan and its phase boundaries. Do not resume against a different plan silently.
5. Inspect the working tree and existing changes. Never overwrite or discard user changes. If previous work cannot be confidently attributed to phases, stop and ask how to proceed.
6. Before the first application change, capture baseline test counts as required by the Testing section.

### 5.2 Execute only the current phase

1. Announce the current phase number, objective, scope, and completion criteria.
2. Implement only tasks belonging to this phase, following the frontend/backend rules below.
3. Run phase-relevant tests and checks. Fix failures introduced by this phase, but do not implement tasks belonging to a later phase.
4. Check the phase's completion criteria and relevant acceptance criteria. Record failed or unexecuted checks accurately.
5. Update the progress log to `AWAITING_APPROVAL` (or `BLOCKED`) with a concise list of modified files, verification commands/results, deviations, and open issues. Do not set `APPROVED` yourself.
6. Present a **Phase review checkpoint** in German: phase status, what changed and why, changed files, tests executed/results, how the user can inspect the diff, outstanding risks, and the exact next phase.
7. **END THE RESPONSE AND WAIT.** Do not run any tool or modify any application file for the next phase before the user's explicit approval.

### 5.3 Approval and requested changes

- Only an explicit user message approving the **specific completed phase** permits progress. Plan approval, Codex status, tool permissions, or prior generic instructions are not phase approval.
- If the user requests corrections, remain in the same phase, apply only the requested/in-scope corrections, reverify, and present the phase again. Wait for approval again.
- On approval, record the phase as `APPROVED` in the progress log with the user's approval reference/date when available. Then start **only the next pending phase**. The next phase must end at its own approval gate.
- If the user approves the final phase, proceed to final verification, acceptance-criteria verification, and final self-review; report the results. If final verification requires additional application changes, request approval before modifying previously approved scope.
- Never create commits or push changes without explicit request.

### 5.4 Missing phase structure and blockers

If an older approved plan lacks explicit phases, propose a small phase breakdown and **ask for approval of that breakdown before implementing**. Do not silently invent phases.

Stop and ask the user before proceeding if there is a blocker, a business/architecture decision, a significant deviation, scope expansion, an unexpected repository state, or uncertainty about prior approval.

The Testing and Acceptance criteria verification sections remain binding. Run targeted tests per phase; run the complete verification suite for affected repositories after the last approved phase, subject to the documented environment prerequisites.

## 6. Frontend implementation

Follow existing Angular/Ionic conventions:

- Preserve NgModule architecture.
- Use existing signal-based services.
- Reuse REST clients and DTO patterns.
- Preserve the state-update pattern: update signals after a successful response, restore the snapshot on error.
- Respect active-group filtering.
- Reuse WebSocket infrastructure.
- Maintain i18n for de/en/es.
- Preserve existing navigation and error handling.

Do not introduce new state-management libraries without approval.

## 7. Backend implementation

Follow existing Spring Boot conventions:

- Keep controllers thin.
- Put business logic in services.
- Reuse DataLoaderService and VerificationService.
- Preserve group-level authorization.
- Use DTOs at API boundaries.
- Follow existing exception handling.
- Use transactions where necessary.
- Use Liquibase for schema changes.
- Never modify already-applied changesets.

For shared group data, evaluate WebSocket notifications.

Ensure notifications reflect successfully committed changes.

## 8. Cross-repository verification

Verify:
- REST contracts match
- DTOs match
- Authorization is correct
- Error handling is consistent
- WebSocket topics and payloads match
- Frontend state updates correctly
- Existing behavior remains compatible

Only modify repositories where necessary.

## 9. Testing

Follow the Testing sections of the root, `spring-backend` and `ionic-frontend` CLAUDE.md files (test level, suffix, `TestData`/`Fixtures`, `src/testing/`).

Before the first change, record the current test count of the affected side(s).

Perform relevant verification for the affected side(s):
- Backend: `./mvnw test` (unit specs, fast) while working; `./mvnw verify` (all specs, Docker must be running) before finishing
- Frontend: `npm run test:ci`, `npm run lint`, `npm run build`
- Single specs: `./mvnw test -Dtest=XyzSpec`, `./mvnw verify -Dit.test=XyzIntegrationSpec -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false`, `npm run test:ci -- --include='**/xyz.service.spec.ts'`
- Tests for new or changed behavior; for a bugfix a regression test that fails without the fix
- Manual verification recommendations

Report the commands that ran and the test count before/after; a count that didn't go up after adding a spec usually means a wrong suffix or file name.

Do not rebuild the test infrastructure.

Never claim tests passed unless actually executed.

## 10. Acceptance criteria verification

Read every acceptance criterion from the plan.

For each criterion, determine:
- Implemented
- Partially implemented
- Not implemented
- Not verified

Explain remaining gaps.

Do not silently omit acceptance criteria.

## 11. Final self-review

Before finishing:
- Compare the diff against the approved plan.
- Check for unrelated changes.
- Check frontend/backend consistency.
- Check authorization and data integrity.
- Check error handling and rollback.
- Check for unnecessary complexity.
- Check for accidentally exposed secrets.

## 12. Restrictions

- Do not modify CLAUDE.md files.
- Do not update Jira automatically.
- Do not commit or push changes.
- Do not implement unrelated features.
- Do not add offline functionality unless requested.
- Do not discard existing user changes.

## 13. Final response

Respond in German.

Include:

### Implementierte Änderungen
For intermediate responses, report only the current phase and end with a request for explicit approval. The final response after all approved phases must report the implementation status of each planned phase:
- Completed
- Partially completed
- Not started

Summarize the changes and verification results per phase.

### Acceptance Criteria
Report fulfillment status for every criterion.

### Verifikation
List actual executed commands and results.

### Abweichungen
Document deviations from the approved plan.

### Risiken
Describe unresolved risks.

### Optionale Verbesserungen
Suggest improvements outside the approved scope.

### Repository-Auswirkungen
Summarize frontend and backend impact.

Do not claim completion if important criteria remain unfulfilled.
