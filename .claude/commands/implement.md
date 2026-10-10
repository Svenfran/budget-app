
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
2. Use the branch name `feature/<ISSUE-KEY>`.
   Example: `feature/DIV-123`.
3. Check the current Git branch and working tree status.
4. Check whether the target branch exists locally.
5. If it does not exist locally, check whether it exists on
   the remote.
6. If the branch already exists:
    - Stay on it if it is already checked out.
    - Otherwise switch to the existing branch.
    - If it exists only on the remote, create a local tracking
      branch without overwriting remote history.
7. If the branch does not exist:
    - Create it from the configured base branch.
    - Prefer `develop` if available; otherwise use the
      repository's configured default branch.
    - Do not automatically pull, merge or rebase.
8. Confirm the active branch before starting implementation.

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
- Confirm the user has approved the plan.
- Check for unresolved blocking questions.
- Inspect the current Git working tree.
- Preserve unrelated existing changes.

Do not infer approval solely from the existence of a plan file.

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

## 5. Frontend implementation

Follow existing Angular/Ionic conventions:

- Preserve NgModule architecture.
- Use existing signal-based services.
- Reuse REST clients and DTO patterns.
- Preserve optimistic updates and rollback.
- Respect active-group filtering.
- Reuse WebSocket infrastructure.
- Maintain i18n for de/en/es.
- Preserve existing navigation and error handling.

Do not introduce new state-management libraries without approval.

## 6. Backend implementation

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

## 7. Cross-repository verification

Verify:
- REST contracts match
- DTOs match
- Authorization is correct
- Error handling is consistent
- WebSocket topics and payloads match
- Frontend state updates correctly
- Existing behavior remains compatible

Only modify repositories where necessary.

## 8. Testing

Use existing project build and test commands.

Perform relevant verification:
- Frontend build and lint
- Backend compilation and tests
- Relevant existing unit/integration tests
- Focused tests for changed behavior where practical
- Manual verification recommendations

Do not rebuild the test infrastructure.

Never claim tests passed unless actually executed.

## 9. Acceptance criteria verification

Read every acceptance criterion from the plan.

For each criterion, determine:
- Implemented
- Partially implemented
- Not implemented
- Not verified

Explain remaining gaps.

Do not silently omit acceptance criteria.

## 10. Final self-review

Before finishing:
- Compare the diff against the approved plan.
- Check for unrelated changes.
- Check frontend/backend consistency.
- Check authorization and data integrity.
- Check error handling and rollback.
- Check for unnecessary complexity.
- Check for accidentally exposed secrets.

## 11. Restrictions

- Do not modify CLAUDE.md files.
- Do not update Jira automatically.
- Do not commit or push changes.
- Do not implement unrelated features.
- Do not add offline functionality unless requested.
- Do not discard existing user changes.

## 12. Final response

Respond in German.

Include:

### Implementierte Änderungen
Summarize changes and affected files.

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
