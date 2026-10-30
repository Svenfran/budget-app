---
description: Independently review Divvy implementation changes using OpenAI Codex CLI
argument-hint: "[DIV-123 | git diff | feature | subsystem | review instructions]"
disable-model-invocation: true
---

# Review — Divvy (Codex)

Conduct an independent, evidence-based review with **Codex CLI** of:

$ARGUMENTS

Work from the `budget-app` monorepo root. Report in German. Claude orchestrates and summarizes; **Codex performs the substantive review**. Do not silently substitute a Claude-only review.

## 1. Resolve target and review baseline

### 1.1 Jira issue (`DIV-[0-9]+`)

1. Normalize the issue key to uppercase and read `docs/plans/<ISSUE-KEY>/implementierungs-plan.md` if present.
2. Read `docs/plans/<ISSUE-KEY>/implementation-progress.md` if present. Treat its statuses as context, **not independent proof of user approval**.
3. If available, retrieve the Jira Story and relevant parent/Epic via configured Atlassian MCP (`.ai/jira-integration.md`). Story requirements define scope; Epic provides context only.
4. Compare implementation against the applicable approved plan, acceptance criteria, project conventions, and actual repository code. State when plan approval cannot be established.
5. Determine the feature branch baseline from the configured base branch (prefer `develop` if appropriate), using the merge-base, and include committed changes since that baseline **plus staged, unstaged, and untracked changes**. Never assume `git diff` alone captures the full implementation.
6. If frontend and backend are separate Git repositories, identify and review the relevant changes in each; do not assume a single Git history.
7. If the comparison baseline is ambiguous or unsafe to infer, ask the user rather than reviewing an arbitrary diff.

### 1.2 Git diff or explicit comparison

Use the specified commit/branch comparison if provided. Otherwise review staged, unstaged and untracked changes against HEAD. Explain the exact scope and note any untracked files that could not be inspected. Do not require Jira for this mode.

### 1.3 Feature or subsystem

For a named feature or architecture subsystem without a Jira key, review relevant implementation and tests. Establish scope and limitations explicitly; do not invent binding requirements. Retain the specialized review checks below where applicable.

## 2. Codex availability and safe execution

1. Verify `codex --version`. If unavailable, stop with a clear error; do not perform a Claude-only substitute.
2. Inspect repository/working-tree status and establish the review scope **before** launching Codex.
3. Build a self-contained prompt that includes the resolved target, plan path, baseline, relevant repository paths, project constraints, and the detailed review rubric below.
4. Invoke Codex non-interactively with `codex exec --sandbox read-only --output-last-message <TEMP_OUTPUT> -` and pass the prompt on stdin. Adapt shell quoting for the environment (e.g. PowerShell). Do not use sandbox bypasses, `--full-auto`, or automatic approval flags.
5. Use temporary prompt and output files outside the repository where practical. Validate exit status and nonempty output. If Codex fails, report the error and **do not** fabricate a review.
6. Codex must not edit project files, tests, migrations, Jira, or Git history. Claude may write the review report artifact only after successful Codex execution and with overwrite approval where required.
7. No automatic fix, re-review, commit, or implementation step.

## 3. Independent Codex review prompt

Instruct Codex:

> You are an independent senior engineer reviewing Divvy code. Inspect the actual repository, the relevant `CLAUDE.md` files, approved architecture decisions, tests, and the supplied implementation plan where applicable. Independently verify claims; do not trust the plan or Claude's implementation summary as evidence. Use the exact supplied review baseline and include uncommitted and untracked changes where accessible. Apply the priorities and specialized checks below. Identify actionable defects and test gaps with concrete file/line evidence, impact, reproduction scenario where possible, and minimal recommended fixes. Distinguish verified defects, plausible risks, and optional improvements. Do not invent requirements. Do not modify files. Write the complete report in German.

Pass the following review rubric (sections 4–9) in the Codex prompt, **not merely in Claude's private instructions**.

## 4. Project context and architecture alignment

For every review mode:

1. Read the root `CLAUDE.md` and relevant subproject `CLAUDE.md` files.
2. Inspect actual source code rather than relying solely on documentation.
3. Consider potential frontend/backend effects, but inspect only areas relevant to the request.
4. Identify applicable concepts under `architecture` and read them before judging architecture compliance.
5. Distinguish approved decisions from drafts, proposals, and recommendations. Only confirmed approved decisions are binding.
6. Support architecture findings with code evidence and the specific decision involved. Do not invent architecture rules or treat personal preferences as violations.
7. If an applicable concept is missing, unapproved, or conflicts with Jira or the plan, state that explicitly. Use existing project conventions and established engineering practices for nonbinding assessments.

For tests, test infrastructure or test data, the binding conventions are the Testing sections of the root, `spring-backend` and `ionic-frontend` CLAUDE.md files (approved and implemented with Epic DIV-3). The background concept `docs/architecture/testarchitektur-konzept.md` is local only and may be missing; use it for rationale, not as the rule set.

## 5. Review priorities

Prioritize findings by severity, impact, likelihood, and relevance:

1. Correctness and functional regressions
2. Security and authorization
3. Data integrity and consistency
4. Concurrency and race conditions
5. Reliability and error handling
6. Frontend/backend compatibility
7. Performance and resource usage
8. Test quality and meaningful coverage gaps
9. Maintainability and readability

Do not report subjective style preferences as defects. Prefer a few well-supported findings over speculative lists.

## 6. Cross-repository checks

When relevant, examine:

**Frontend (`ionic-frontend`):** Components and lifecycle; Angular Signals and state consistency; REST requests and DTOs; state updates after success and snapshot rollback on error; WebSocket subscriptions; resource cleanup; error handling and i18n.

**Backend (`spring-backend`):** Controllers and services; authorization and group membership; DTO contracts and validation; transactions and persistence; exception handling; WebSocket notifications; Liquibase migrations; resource usage.

Check contract consistency across repositories when changes cross the boundary. State when a repository is unaffected only if that conclusion is supported by the inspected scope.

## 7. Specialized reviews

Apply these additional checks **only** when relevant to the requested target.

### 5.1 WebSocket architecture

Examine connection lifecycle and reconnection; login/logout and group changes; subscription management and cleanup; duplicate, missing, or out-of-order events; concurrent updates; frontend state synchronization; transaction boundaries and notification timing; authorization and recipient selection; error recovery, performance, and recovery after extended disconnection.

Consider future offline compatibility when relevant, without expanding the review into an unrelated offline implementation proposal.

### 5.2 Testing architecture

Examine frameworks and configuration; separation of unit, integration, API, and E2E tests where applicable; test discovery and execution; isolation and determinism; mocking; duplicated setup; test-data builders, factories, and fixtures; database setup and cleanup; readability; meaningful coverage gaps; and build workflows.

For a testing **architecture review**, propose a pragmatic frontend/backend target architecture, naming and organization conventions, reusable test-data patterns, examples grounded in existing code, and an incremental migration strategy. Separate infrastructure improvements from coverage expansion. Prefer existing frameworks where reasonable and avoid wholesale rewrites without justification.

For ordinary feature or diff reviews, assess only the relevant tests; do not require a full testing architecture proposal.

## 8. Findings and recommendations

For each material finding, provide:

- **Severity:** Critical, High, Medium, or Low.
- **Category:** Confirmed defect, architecture violation, plausible risk, or optional improvement.
- **Evidence:** Relevant file and line reference when available, plus concrete code or configuration evidence.
- **Impact:** Actual or potential consequence; state uncertainty where applicable.
- **Recommendation:** Specific, proportionate correction or next step.
- **Effort:** Small, Medium, or Large, when reasonably estimable.

Classify the **recommended action** separately from severity:

- **Must Fix:** Necessary for correctness, security, data integrity, or an explicitly binding requirement.
- **Should Fix:** Meaningful improvement to reliability, maintainability, performance, or test confidence.
- **Optional:** Nonessential improvement, alternative design, or broader refactoring.

Do not automatically label every architectural preference as Must Fix. Rank findings by impact and likelihood. Do not invent issues to fill a report; distinguish verified behavior from hypotheses.

## 9. Testing assessment

Evaluate tests relevant to the review: important behavior and regression coverage, reliability, isolation, integration boundaries, and reusable test infrastructure. Check in particular: a regression test for bugfixes, correct suffix/file name (a wrong one silently doesn't run — compare test counts), and adherence to the Testing conventions in the CLAUDE.md files.

Distinguish tests that are missing, tests that exist but could not be run, and tests that ran and failed. If tests are executed, report the command and observed result; never claim execution without running them. Do not demand new frameworks without justification.

## 10. Codex output requirements

Require these sections in the **Codex-generated** German review:

1. **Review-Scope:** baseline, repositories, changed files, plan/Jira sources, limitations.
2. **Gesamtbewertung:** `APPROVED` | `CHANGES_REQUESTED` | `DECISION_REQUIRED`; `APPROVED` means no material findings in the reviewed scope, **not** user approval.
3. **Findings:** IDs `F-001` etc.; severity `Critical`/`High`/`Medium`/`Low`; category (confirmed defect / architecture violation / plausible risk / optional improvement); `Must Fix`/`Should Fix`/`Optional`; evidence with file and line; impact; specific fix; estimated effort if feasible.
4. **Acceptance Criteria:** each applicable criterion `Erfüllt` / `Teilweise` / `Nicht erfüllt` / `Nicht verifiziert`, with evidence.
5. **Tests und Verifikation:** coverage gaps; commands actually run and results; distinguish not run, failed, and passed.
6. **Offene Entscheidungen und Risiken:** separate user decisions from technical findings.
7. **Empfohlene nächste Schritte:** prioritize minimal fixes, avoid unrelated refactorings.

Do not claim tests passed without running them. Do not invent findings to populate the report. For Jira mode, flag any incomplete or unapproved phases and explain how that limits final review confidence.

## 11. Save and present results

- For Jira mode save to `docs/plans/<ISSUE-KEY>/codex-implementation-review.md` after a successful Codex run. Do not overwrite an existing report without explicit user approval. For other modes, present the Codex output in the conversation unless the user specifies a report path.
- Include in the saved report the review baseline (branch, base/merge-base where applicable), review date, plan reference, and note that the findings are Codex-generated. Preserve Codex findings faithfully; label any extra Claude observations separately.
- Summarize findings in German, prioritizing Must Fix and open decisions. Do not automatically implement recommendations or invoke `/implement`.
- Ask the user how to proceed. If fixes are approved, changes must follow the existing implementation phase approval rules or a separately approved corrective phase; do not silently reopen completed phases.
- A changed implementation requires a new review before treating the prior result as current.
- Do not overwrite an existing review. If a previous review exists, save the new review to `docs/plans/<ISSUE-KEY>/codex-implementation-review-<NUMBER>.md`.

## 12. Restrictions

- Review only; no application code changes, dependency installation, migrations, Jira edits, commits, pushes, resets, or destructive Git operations.
- Do not modify plans, `CLAUDE.md`, or architecture documents.
- Do not replace Codex with Claude when Codex fails.
- Do not interpret `APPROVED` as a human approval or a permission to merge.
