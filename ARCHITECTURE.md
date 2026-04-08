# ARCHITECTURE.md

## Flyer – Zero-Cost-First Architecture

### Status: Active Design

### Owner: Brad

---

## 🧭 Key Architectural Decisions

1. **Offline-first Android client**

   * The app must be fully usable without a network connection
   * Local data is the source of truth for UI

2. **Thin backend only for shared state**

   * Backend handles identity, canonical data, and moderation
   * No heavy business logic in early phases

3. **Serverless over servers**

   * No always-on backend infrastructure
   * Use edge/serverless platforms only

4. **Community-driven data model**

   * Events are primarily user-submitted
   * Moderation layer promotes submissions to canonical records

5. **Zero-cost-first constraint**

   * All early infrastructure must fit within free tiers
   * Paid services are only introduced when justified by usage

---

## 🧱 System Overview

```text
Android App (Kotlin / Jetpack Compose)
    |
    |-- Room (local DB)
    |-- WorkManager (sync)
    |-- Local search / ranking
    |-- Offline-first UI
    |
    v
Cloudflare Workers (API layer)
    |
    |-- Auth verification
    |-- Rate limiting
    |-- Validation
    |-- Moderation endpoints
    |
    v
Supabase
    |-- Postgres
    |-- Auth
    |-- Storage (limited)
```

---

## 📱 Android Client Responsibilities

### Core Responsibilities

* Local data storage (Room)
* UI rendering (Compose)
* Search, filtering, ranking
* Offline functionality
* Background sync (WorkManager)

### Local Data

* events
* venues
* artists
* follows
* cached queries

### Behavior

* Read from local DB first
* Sync in background
* Batch writes
* Retry failed requests automatically

---

## ☁️ Backend Responsibilities

### Cloudflare Workers

* Request validation
* Auth token verification
* Rate limiting
* API routing
* Moderation endpoints
* Scheduled jobs

### Supabase

* Canonical database
* Authentication
* Row-level security
* Limited storage

---

## 🗃️ Data Model

### Tables

* users
* artists
* venues
* events
* event_submissions
* user_follows
* event_votes
* edit_history

---

## ⚠️ Critical Rule: Submissions vs Canonical Data

Submissions are never written directly to canonical tables.

Flow:

1. User submits event → `event_submissions`
2. Review occurs (manual or automated)
3. Approved → merged into `events`
4. History preserved

---

## 🔄 Sync Model

### Read Flow

1. Load local data immediately
2. Check staleness
3. Fetch delta updates
4. Update local DB
5. UI reacts to DB changes

### Write Flow

* Queue locally
* Send asynchronously
* Retry on failure
* Ensure idempotency

---

## 🔍 Search & Ranking

### Phase 1 (Local Only)

* Nearby events
* Upcoming events
* Followed artists
* Genre matching

### Later

* Server-side ranking (optional)
* Advanced search index (only if needed)

---

## 🔌 Realtime Policy

Allowed:

* Moderation tools
* Admin dashboards

Avoid:

* Realtime UI everywhere

---

## 💾 Storage Rules

Allowed:

* Small images
* Compressed flyers

Avoid:

* Large media
* Video/audio hosting

---

## 🛡️ Moderation Model

### Phase 1

* Manual approval
* Simple moderation queue

### Phase 2

* Trust scoring
* Community voting

---

## 📂 Repository Structure

```text
flyer/
├── android-app/
├── backend-workers/
├── database-schema/
├── docs/
├── community/
└── ops-secrets/ (private)
```

---

## 🚀 Deployment Phases

### Phase 0

* Local-only app
* Sample data

### Phase 1

* Supabase (free)
* Cloudflare Workers (free)
* Auth + submissions

### Phase 2

* Community features
* Trust system

### Phase 3

* Scale selectively
* Introduce paid services only when necessary

---

## ⚖️ Decision Rules

Add backend only if:

* Requires shared state
* Security-sensitive
* Cannot run on device

Avoid if:

* Local solution exists
* Simpler heuristic works

---

## 🎯 Guiding Principle

> Flyer is local-first, server-assisted—not server-dependent.

---

## 🧪 Initial Build Order

1. Android local app shell
2. Local database + UI
3. Sync layer
4. Backend connection
5. Submission system
6. Moderation tools

---

## 📌 Summary

* Keep it simple
* Keep it local-first
* Keep it cheap
* Let the community power the data

If this architecture holds, Flyer can grow organically without infrastructure becoming a blocker.
