---
description: Review Jira features, Git changes or existing Divvy architecture
argument-hint: "[DIV-123 | git diff | feature | subsystem | review instructions]"
disable-model-invocation: true
---

# Review — Divvy

Perform a focused, evidence-based, read-only review of:

$ARGUMENTS

Work from the `budget-app` monorepo root. Respond in German.

## 1. Resolve review target

Choose the mode that best matches the request. If ambiguous, state the assumption and ask only if it materially changes the review. Apply additional instructions in `$ARGUMENTS` within the chosen scope.

### 1.1 Jira issue review

If the input starts with a Jira issue key matching `DIV-[0-9]+` (case-insensitive):

1. Normalize the key to uppercase.
2. Read `docs/plans/<ISSUE-KEY>/implementierungs-plan.md` if it exists. Treat it as approved only when approval is established; existence alone is not approval.
3. Retrieve the issue using the configured Atlassian MCP integration when available. Read its summary, description, acceptance criteria, status, and relevant links or dependencies.
4. Identify and, when accessible, read the parent issue or Epic: its objectives, relevant requirements, and technical context.
5. Determine the scope from the requested issue and the applicable approved plan. The parent/Epic provides context, not additional Story scope. Do not import sibling Story requirements.
6. Inspect relevant implementation, surrounding code, tests, and Git changes where useful.
7. Compare against the issue requirements, approved plan, applicable approved architecture decisions, and project conventions.

If the plan is missing, say so and ask whether to continue without it. If Jira is unavailable, report the limitation and use an existing plan only if it contains sufficient requirements; otherwise ask for the missing information. If only the parent/Epic is inaccessible, report that and proceed when the issue can still be reviewed meaningfully.

Report conflicts among Jira, the plan, and architecture concepts explicitly; do not silently resolve them. Do not invent requirements or acceptance criteria.

### 1.2 Git diff review

Use this mode for current changes, `git diff`, staged/unstaged/untracked files, or branch/commit comparisons.

1. Use the specified comparison target; if none is provided, review staged, unstaged, and untracked changes against HEAD.
2. Inspect the diff, relevant surrounding code, and affected tests.
3. Evaluate correctness, regressions, security, compatibility, and applicable architecture decisions.
4. Report findings supported by evidence in the changes or their consequences.

Do not require a Jira issue or implementation plan.

### 1.3 Feature review

Use this mode for an implemented feature without a Jira key.

1. Identify the relevant frontend and/or backend code and tests.
2. Evaluate behavior, edge cases, error handling, authorization where relevant, state consistency, cross-repository contracts, test quality, and maintainability.
3. Check applicable project conventions and approved architecture decisions.

Do not require Jira or a plan. Do not present undocumented assumptions as binding feature requirements.

### 1.4 Architecture review

Use this mode for an existing subsystem or architectural concern, such as WebSocket synchronization, authentication, Angular state management, REST APIs, persistence, or testing infrastructure.

1. Define the subsystem boundaries and inspect actual code, configuration, and tests.
2. Read relevant project conventions and architecture documentation.
3. Evaluate correctness, reliability, security and data integrity where applicable, maintainability, duplication, extensibility, testability, and technical risks.
4. Identify deviations from approved decisions and propose concrete, proportionate improvements.
5. For a requested target architecture, explain the recommended design and incremental migration path.

Distinguish confirmed defects, architecture violations, plausible risks, and optional design alternatives. An architecture review may recommend changing an approved concept but must not silently redefine it.

## 2. Project context and architecture alignment

For every review mode:

1. Read the root `../../CLAUDE.md` and relevant subproject `CLAUDE.md` files.
2. Inspect actual source code rather than relying solely on documentation.
3. Consider potential frontend/backend effects, but inspect only areas relevant to the request.
4. Identify applicable concepts under `architecture` and read them before judging architecture compliance.
5. Distinguish approved decisions from drafts, proposals, and recommendations. Only confirmed approved decisions are binding.
6. Support architecture findings with code evidence and the specific decision involved. Do not invent architecture rules or treat personal preferences as violations.
7. If an applicable concept is missing, unapproved, or conflicts with Jira or the plan, state that explicitly. Use existing project conventions and established engineering practices for nonbinding assessments.

For testing infrastructure, test data, test conventions, or JUnit-to-Spock migration, explicitly check `../../docs/architecture/testarchitektur-konzept.md` if it exists. Do not assume it is approved.

## 3. Review priorities

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

## 4. Cross-repository checks

When relevant, examine:

**Frontend (`ionic-frontend`):** Components and lifecycle; Angular Signals and state consistency; REST requests and DTOs; optimistic updates and rollback; WebSocket subscriptions; resource cleanup; error handling and i18n.

**Backend (`spring-backend`):** Controllers and services; authorization and group membership; DTO contracts and validation; transactions and persistence; exception handling; WebSocket notifications; Liquibase migrations; resource usage.

Check contract consistency across repositories when changes cross the boundary. State when a repository is unaffected only if that conclusion is supported by the inspected scope.

## 5. Specialized reviews

Apply these additional checks **only** when relevant to the requested target.

### 5.1 WebSocket architecture

Examine connection lifecycle and reconnection; login/logout and group changes; subscription management and cleanup; duplicate, missing, or out-of-order events; concurrent updates; frontend state synchronization; transaction boundaries and notification timing; authorization and recipient selection; error recovery, performance, and recovery after extended disconnection.

Consider future offline compatibility when relevant, without expanding the review into an unrelated offline implementation proposal.

### 5.2 Testing architecture

Examine frameworks and configuration; separation of unit, integration, API, and E2E tests where applicable; test discovery and execution; isolation and determinism; mocking; duplicated setup; test-data builders, factories, and fixtures; database setup and cleanup; readability; meaningful coverage gaps; and build workflows.

For a testing **architecture review**, propose a pragmatic frontend/backend target architecture, naming and organization conventions, reusable test-data patterns, examples grounded in existing code, and an incremental migration strategy. Separate infrastructure improvements from coverage expansion. Prefer existing frameworks where reasonable and avoid wholesale rewrites without justification.

For ordinary feature or diff reviews, assess only the relevant tests; do not require a full testing architecture proposal.

## 6. Findings and recommendations

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

## 7. Testing assessment

Evaluate tests relevant to the review: important behavior and regression coverage, reliability, isolation, integration boundaries, and reusable test infrastructure.

Distinguish tests that are missing, tests that exist but could not be run, and tests that ran and failed. If tests are executed, report the command and observed result; never claim execution without running them. Do not demand new frameworks without justification.

## 8. Review output

Respond in German. Scale detail to the scope and findings; avoid empty or repetitive sections.

For all review modes, include:

1. **Zusammenfassung:** Scope, overall assessment, and important limitations.
2. **Findings:** Prioritized, evidence-based findings; explicitly state if none are significant.
3. **Empfohlene Maßnahmen:** Must Fix / Should Fix / Optional where applicable.
4. **Testqualität und Testlücken:** Relevant observations, including test execution status if checked.
5. **Gesamtbewertung:** Main risks, strengths, and recommended next steps.

Add **Frontend-/Backend-Auswirkungen** when the review crosses repositories or contract boundaries.

For architecture reviews, additionally include **Zielarchitektur** and **Migrationsstrategie** when the request calls for recommendations or redesign. For focused diff reviews, keep the report concise and omit irrelevant sections.

## 9. Restrictions

- This is a read-only review: do not modify files, implement fixes, create commits, or introduce dependencies.
- Do not modify Jira issues, implementation plans, or architecture concepts.
- Do not expand the requested scope unnecessarily or recommend major refactorings without concrete justification.
- Do not present speculative risks as confirmed defects.
- Wait for explicit approval before implementing recommendations.
