---
description: Independently review an implementation plan using OpenAI Codex CLI
argument-hint: "[DIV-123 | plan-directory-name]"
disable-model-invocation: true
---

# Review Plan — Divvy

Perform an independent, evidence-based review of the implementation plan for:

$ARGUMENTS

## 1. Resolve review target

Work from the `budget-app` monorepo root.

1. Require exactly one plan identifier.
2. If the input matches `DIV-[0-9]+`, normalize it to uppercase.
3. Otherwise, treat the input as an existing plan directory identifier.
4. Reject absolute paths, path traversal and identifiers containing directory separators.
5. Resolve the plan path:

   `docs/plans/<IDENTIFIER>/implementierungs-plan.md`

6. Verify that the file exists and is readable.
7. Do not generate a new implementation plan if the file is missing.

The review output path is:

`docs/plans/<IDENTIFIER>/codex-review.md`

Do not overwrite an existing review without explicit user approval.

## 2. Project context

- Read the root `CLAUDE.md`.
- Read relevant frontend and backend `CLAUDE.md` files.
- Follow `.ai/jira-integration.md` when Jira access is needed.
- Respect approved architecture documents.
- Treat the implementation plan as a proposal, not as verified truth.
- Inspect actual repository code to validate assumptions.

The requested Jira Story defines the scope. Parent issues and Epics provide context but must not automatically expand the scope.

## 3. Verify Codex availability

Check that Codex CLI is installed:

`codex --version`

If unavailable, stop and explain how to install or configure it.

Do not silently replace the independent Codex review with a Claude self-review.

## 4. Execute independent review

Invoke Codex CLI non-interactively from the repository root.

Use:

- `codex exec`
- `--sandbox read-only`
- `--output-last-message` to capture the review
- No automatic approval bypass
- No dangerous sandbox bypass
- No application code modifications

Use a separate review prompt containing the following instructions:

### Codex review instructions

You are an independent senior software engineer reviewing an implementation plan for the Divvy application.

Review the plan against the actual repository implementation.

Read the relevant `CLAUDE.md` files and architecture documentation.

Evaluate:

1. Requirements and acceptance criteria
    - Are all requirements covered?
    - Are assumptions or business rules invented?
    - Are important edge cases missing?

2. Technical correctness
    - Are descriptions of existing code accurate?
    - Do referenced classes, methods and files exist?
    - Are proposed changes technically feasible?

3. Architecture and consistency
    - Does the solution follow existing conventions?
    - Are frontend/backend contracts consistent?
    - Are authorization, transactions, concurrency and state synchronization considered where relevant?

4. Testing
    - Are planned tests sufficient?
    - Are expectations logically correct?
    - Are existing test utilities reused?
    - Are important regression cases missing?

5. Risks and maintainability
    - Are there implementation blockers?
    - Are unnecessary refactorings proposed?
    - Are simpler alternatives available?

6. Open decisions
    - Identify questions requiring human approval.
    - Recommend a solution where sufficient evidence exists.
    - Never invent missing business requirements.

### Review rules

- Review independently.
- Base findings on actual code and documentation.
- Reference concrete files, methods and line numbers where possible.
- Distinguish verified findings from assumptions.
- Do not report speculative problems as confirmed bugs.
- Do not expand the Jira scope without justification.
- Do not modify any files.
- Do not implement the plan.
- Do not create commits.

### Required output format

Write the review in German.

# Codex Plan Review

## 1. Ergebnis

Status: APPROVED | CHANGES_REQUESTED | DECISION_REQUIRED

Short overall assessment.

## 2. Findings

For each finding:

- ID: F-001, F-002, ...
- Severity: BLOCKER | MAJOR | MINOR
- Category
- Description
- Evidence (file, method, line where possible)
- Impact
- Recommended action

Only include actionable findings.

## 3. Open decisions

For each decision:

- ID: D-001, D-002, ...
- Question
- Available options
- Advantages and disadvantages
- Recommended option
- Reasoning
- Requires user approval: Yes/No

Do not make business decisions on behalf of the user.

## 4. Suggested plan changes

List concrete, minimal changes required before implementation.

Separate required changes from optional improvements.

## 5. Verification assessment

Evaluate the planned tests and verification commands.

## 6. Final recommendation

State whether the plan is ready for human approval.

Use APPROVED only if there are no unresolved blockers, major findings or required human decisions.

Use DECISION_REQUIRED when a blocking human decision is necessary.

Otherwise use CHANGES_REQUESTED when corrections are required.

## 5. Save review

Execute Codex and capture its final response in:

`docs/plans/<IDENTIFIER>/codex-review.md`

Prefer a temporary output file first, then move it to the final destination only after successful execution and validation.

Do not overwrite an existing review without approval.

If Codex fails, times out or produces an empty result:

1. Report the failure.
2. Preserve the implementation plan.
3. Do not create an APPROVED review.
4. Do not silently substitute Claude's own assessment.

## 6. Present results

After Codex finishes:

1. Read the complete review.
2. Summarize the findings in German.
3. Highlight BLOCKER and MAJOR findings.
4. Present open decisions with Codex recommendations.
5. Identify findings that may require further verification.
6. Clearly distinguish Codex findings from any additional Claude observations.

Do not automatically dismiss or modify Codex findings.

## 7. Human approval gate

Ask the user how to proceed.

Possible actions:

- Accept the review and approve the existing plan.
- Revise the implementation plan based on selected findings.
- Discuss unresolved decisions.
- Request another independent review.

Do not automatically revise the implementation plan.

Do not automatically invoke `/implement`.

Do not claim that a plan has been approved unless the user explicitly approves it.

## 8. Restrictions

- Do not modify application code.
- Do not modify tests or migrations.
- Do not modify the implementation plan.
- Do not modify `CLAUDE.md` files.
- Do not commit changes.
- Do not update Jira.
- Do not implement the plan.
- Do not start automatic review loops.
- Do not bypass Codex sandbox restrictions.

## 9. Final response

Respond in German.

Include:

1. Review status
2. Number of findings by severity
3. Most important findings
4. Open decisions and recommendations
5. Required plan changes
6. Review file path
7. Recommended next action

Wait for the user's decision.
