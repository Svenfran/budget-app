
---
description: Create an implementation plan from a Jira issue or free-text requirement
argument-hint: "[DIV-123 | free-text requirement]"
disable-model-invocation: true
---

# Plan — Divvy

Create a detailed, reviewable implementation plan for:

$ARGUMENTS

## 1. Resolve input

Determine whether the input is a Jira issue key or a free-text requirement.

### 1.1 Jira issue

If the input matches `DIV-[0-9]+` (case-insensitive):

1. Normalize the issue key to uppercase.
2. Retrieve the issue from Jira using the configured Atlassian MCP integration.
3. Read the issue:
   - Summary
   - Description
   - Acceptance criteria
   - Issue type and status
   - Relevant linked issues and dependencies
4. Treat the Jira issue as the source of truth for the requested scope and requirements.
5. Do not modify the Jira issue.

Jira base URL see: `.ai/jira-integration.md`

#### Parent issue and Epic context

After retrieving the requested issue:

1. Determine whether it has a parent issue or belongs to an Epic.
2. If a parent or Epic exists, retrieve its:
   - Issue key and summary
   - Description
   - Goals and relevant requirements
   - Acceptance criteria, if available
   - Relevant architectural or technical constraints
3. Use this information to understand the broader context and objectives.
4. Identify relevant dependencies on other Jira issues where available.
5. Include a concise "Parent / Epic context" section in the implementation plan.

Follow these rules:

- The requested Story defines the implementation scope and acceptance criteria.
- The parent issue or Epic provides broader context and objectives.
- Approved architecture concepts define binding technical constraints.
- Do not automatically include requirements from sibling Stories.
- Do not expand the requested scope based solely on Epic requirements.
- Do not invent missing requirements, acceptance criteria or dependencies.
- If requirements conflict, explicitly report the conflict and request clarification.

If the parent or Epic cannot be retrieved, report the limitation and continue with the requested issue if sufficient information is available.

#### Jira availability

If the requested Jira issue cannot be retrieved:

1. Explain the missing connection, permission or retrieval error.
2. Ask the user to provide the issue description and acceptance criteria.
3. Do not invent issue details.
4. Do not proceed with an incomplete implementation plan.

#### Output path

Save the implementation plan to:

`docs/plans/<ISSUE-KEY>/implementierungs-plan.md`

Example:

`docs/plans/DIV-1/implementierungs-plan.md`

### 1.2 Free-text requirement

If the input is not a Jira issue key:

1. Treat the complete input as the requirement.
2. Generate a short, descriptive kebab-case identifier.
3. Use lowercase ASCII letters, numbers and hyphens.
4. Keep the identifier meaningful and reasonably short.
5. Check whether the target directory already exists.
6. If the directory belongs to another requirement, choose a distinct identifier.

Save the implementation plan to:

`docs/plans/<generated-name>/implementierungs-plan.md`

Example:

`docs/plans/shopping-list-sorting/implementierungs-plan.md`

### 1.3 Existing plans

Before writing any implementation plan:

1. Check whether the target file already exists.
2. If it exists, read it before proceeding.
3. Do not overwrite or replace an existing plan without explicit user approval.
4. Preserve previously approved decisions unless the user explicitly requests changes.
## 2. Project context

- Work from the budget-app monorepo root.
- Read the root CLAUDE.md.
- Read the relevant frontend and backend CLAUDE.md files.
- Analyze both repositories for impact.
- Inspect actual code before proposing changes.
- Reuse existing patterns and conventions.

## 3. Requirements analysis

Identify:
- Business goal
- Functional requirements
- Acceptance criteria
- Expected user behavior
- Edge cases
- Existing behavior that must remain unchanged
- Open decisions

Do not invent business rules.

Ask the user about blocking ambiguities before finalizing the plan.

## 4. Code analysis

Inspect the relevant existing implementation.

Frontend:
- Pages and components
- Angular services and signals
- REST clients and DTOs
- Optimistic updates and rollback
- WebSocket subscriptions
- i18n and error handling

Backend:
- Controllers and services
- Repositories and entities
- DTOs and mappings
- Authorization and validation
- Transactions and exceptions
- Liquibase migrations
- WebSocket notifications
- Existing tests

Identify concrete affected files and reusable patterns.

## 5. Cross-repository consistency

For shared group data, consider:
- REST API contracts
- Group-level authorization
- Frontend state consistency
- WebSocket synchronization
- Concurrent updates
- Transaction boundaries
- Error handling

Explicitly state when one repository requires no changes.

## 6. Scope management

Separate:

### Required changes
Necessary to satisfy the requirements.

### Local improvements
Small, low-risk improvements directly related to the feature.

### Optional improvements
Larger refactorings or unrelated optimizations.

Only required changes and justified local improvements belong in the implementation plan.

Do not introduce new architecture or dependencies unnecessarily.

## 7. Plan structure

Write the plan in German using:

# Implementierungsplan

## Metadaten
- Quelle: Jira or Freitext
- Jira-Key: if applicable
- Jira-URL: if applicable
- Titel
- Status: Entwurf
- Erstellungsdatum

## 1. Anforderung
Summarize the original requirement.

For Jira issues, preserve all acceptance criteria.

## 2. Bestehende Implementierung
Describe relevant existing behavior and reusable patterns.

## 3. Lösungsansatz
Explain the proposed technical solution.

## 4. Frontend-Änderungen
List affected files and intended modifications.

## 5. Backend-Änderungen
List affected files and intended modifications.

## 6. API und Datenmodell
Describe DTO, endpoint, entity and migration changes.

## 7. WebSocket und State-Synchronisierung
Describe real-time updates and state consistency.

## 8. Implementierungsschritte
Provide ordered, actionable steps.

## 9. Risiken und Edge Cases
Include concurrency, authorization and error scenarios.

## 10. Verifikation
Describe builds, tests and manual checks.

Map relevant acceptance criteria to verification steps.

## 11. Optionale Verbesserungen
List improvements outside the feature scope.

## 12. Offene Fragen
Document unresolved decisions.

## 8. Save plan

Save the completed plan to the resolved path.

Create the directory if needed.

The plan must be self-contained so `/implement` can use it in a new Claude Code session without requiring the original conversation.

Do not overwrite an existing plan without asking.

## 9. Restrictions

- Do not modify application code.
- Do not create migrations or tests.
- Do not perform unrelated refactorings.
- Do not modify CLAUDE.md files.
- Do not commit changes.
- Do not update Jira.
- Do not implement the plan.

## 10. Final response

Respond in German.

Summarize:
1. Requirement and source
2. Proposed solution
3. Frontend and backend impact
4. Implementation steps
5. Risks and open questions
6. Optional improvements
7. Saved plan path

Wait for approval before implementation.
