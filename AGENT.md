# AGENT.md

## Purpose

This document defines how AI coding agents should work inside the **Flyer** repository.

Flyer is an Android-first live music discovery project designed around a **zero-cost-first**, **offline-first**, and potentially **open-source** architecture. Agents working in this repo should prioritize simplicity, low infrastructure cost, and incremental delivery over cleverness or premature scale.

This file is intended for use by AI assistants, coding agents, and future collaborators so work remains consistent even across separate sessions and tools.

---

## Project Summary

Flyer is currently:

* Android-first
* built as a small iterative prototype
* intended to start simple and grow carefully
* designed to avoid expensive backend requirements early

The current architectural direction is:

* **Kotlin + Jetpack Compose** for Android
* **Room** for local storage
* **WorkManager** for background sync
* **Cloudflare Workers** for a thin serverless API layer
* **Supabase** for database and auth
* **offline-first UI and behavior**

Flyer should be useful locally first, then synchronized centrally second.

---

## Core Product Philosophy

Agents should preserve these principles in all implementation work.

### 1. Offline-first

The app should continue to function gracefully without a network connection.

### 2. Zero-cost-first

Prefer solutions that fit free tiers and avoid unnecessary hosted complexity.

### 3. Thin backend

Do not introduce heavy backend logic unless a feature truly requires shared global state, security boundaries, or moderation workflows.

### 4. Community-driven data

The product may eventually rely on community-submitted events. Architect data flows with moderation, auditability, and trust in mind.

### 5. Small safe increments

Agents should prefer narrowly scoped, testable changes over broad refactors.

---

## What Agents Should Optimize For

When making implementation decisions, optimize for:

* clarity
* maintainability
* small PRs
* good defaults
* low operating cost
* Android responsiveness
* future iOS portability where practical

Agents should avoid optimizing for:

* speculative scale
* trendy frameworks without strong justification
* complex abstractions before the app needs them
* backend-heavy solutions to client-side problems

---

## General Working Rules

### Make the smallest correct change

Prefer the smallest change that cleanly solves the current problem.

### Preserve existing direction

Do not silently pivot architecture, frameworks, or patterns without clearly explaining why.

### Be explicit

When changing behavior, document:

* what changed
* why it changed
* what assumptions were made
* any follow-up work needed

### Avoid hidden magic

Prefer readable implementation over clever implementation.

### Respect incomplete stages

This repository may intentionally contain placeholder systems, stubs, or temporary scaffolding. Do not “finish everything” unless asked.

---

## Architecture Rules

### Android client is primary

The Android app is the center of the system.

Agents should prefer:

* local persistence
* repository pattern
* unidirectional state flow
* clean Compose state management
* background sync instead of chatty live updates

### Local-first reads

UI should read from local storage first whenever possible.

### Sync should be conservative

Network sync should:

* batch work when possible
* avoid unnecessary polling
* support retries
* be tolerant of partial failure

### Backend should stay thin

Cloudflare Workers should mainly handle:

* request validation
* auth verification
* rate limiting
* moderation workflows
* scheduled maintenance tasks

Supabase should mainly handle:

* canonical data
* auth
* row-level security
* limited storage

### No premature microservices

Do not split the backend into multiple services unless there is a clear operational reason.

---

## Data Rules

### Submissions are not canonical records

User submissions should go into submission-oriented tables or objects first. They should not write directly into canonical public event records.

### Preserve auditability

If a feature changes shared records, preserve enough history to understand who changed what and when.

### Prefer boring schemas

Use straightforward tables and fields. Avoid overdesigned schemas early.

### Minimize storage needs

Avoid features that require large media hosting unless explicitly requested.

---

## UX Rules

### Fast first render matters

The app should feel responsive immediately, even if network refresh happens later.

### Graceful offline states

Offline should not feel like a crash or dead end.

### Empty states should teach

If a screen has no data yet, the UI should help the user understand what to do next.

### Avoid clutter

Keep the interface simple. Flyer should feel focused, not overloaded.

---

## Coding Standards

### Kotlin / Android

Prefer:

* Kotlin idioms
* clear naming
* small composables
* ViewModel-backed screen logic
* immutable UI state where practical
* repository abstraction around storage/network boundaries

Avoid:

* giant composables
* business logic inside UI functions
* tight coupling between network models and UI models
* unnecessary inheritance

### Data flow

Prefer a flow like:

* data source
* repository
* view model
* UI state
* composable rendering

### Error handling

Errors should be:

* explicit
* non-destructive
* user-friendly where surfaced
* logged or traceable where helpful

### Comments

Use comments sparingly but clearly. Explain intent, not obvious syntax.

---

## PR / Change Management Expectations

When an agent proposes or completes work, it should provide:

### 1. Summary

A short explanation of what changed.

### 2. Files changed

A clear list of added or modified files.

### 3. Reasoning

Why this was the right size and shape of change.

### 4. Follow-ups

Any next steps, risks, or cleanup items.

Agents should avoid vague summaries like “improved architecture” or “fixed various issues.”

---

## What Not to Do

Agents should not:

* add paid infrastructure by default
* introduce a full server backend when local/client logic would work
* add analytics bloat without approval
* add scraping systems without discussion
* overbuild recommendation systems early
* replace stable patterns with novel abstractions for style points
* silently reformat or reorganize unrelated files in the same change
* make broad renames unless necessary

---

## Decision Heuristics

When uncertain, prefer the option that is:

1. simpler
2. cheaper to host
3. easier to test
4. easier to explain
5. easier to undo

That order is usually correct for Flyer at this stage.

---

## Suggested Repository Areas

This repo is expected to grow into something like:

```text
flyer/
├── android-app/
├── backend-workers/
├── database-schema/
├── docs/
├── community/
└── ops-secrets/
```

Agents should keep boundaries clear between:

* Android client code
* backend worker code
* database migrations/schema
* docs and contributor guides
* private operational material

---

## Collaboration Guidance for AI Agents

### Do not assume hidden context

Each task should be understandable from repo contents and current instructions.

### Surface assumptions

If a detail is missing, state the assumption used.

### Keep handoffs clean

When finishing a chunk of work, leave a clear handoff note for the next agent or human.

### Respect the roadmap

Do not jump ahead into advanced systems unless the requested task requires it.

---

## Current Priority Order

At this stage, agents should generally prioritize work in this order:

1. Android app shell and local data model
2. Offline-first UX foundations
3. Sync architecture
4. Thin backend integration
5. Community submission flow
6. Moderation and trust systems
7. Nice-to-have enhancements

---

## Definition of a Good Change

A good change in Flyer usually has these qualities:

* solves one clear problem
* matches existing architecture
* does not increase hosting cost unnecessarily
* keeps the app fast
* is easy to review
* leaves the codebase clearer than before

---

## Final Principle

If there is a choice between a flashy solution and a durable one, choose the durable one.

Flyer should be built like a dependable road case, not a fireworks crate.
