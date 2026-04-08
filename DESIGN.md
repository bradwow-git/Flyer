# Flyer — Project Design Document (v1.0)

## 1. 🎯 Project Overview

Flyer is an offline-first Android music player that learns user taste through listening behavior and evolves into a personalized discovery system.

**Primary goals:**
* Deliver a best-in-class local music player
* Use behavior (plays, skips, replays) to drive intelligence
* Remain fully functional offline
* Support future crowd-sourced discovery
* Be designed for eventual iOS parity

## 2. 🧠 Core Philosophy

### 2.1 Local-First
* All core features must work without internet
* No account required
* No dependency on external APIs for core experience

### 2.2 Behavior > Manual Input
System learns from:
* what you play
* what you skip
* what you replay

Not just:
* playlists
* likes alone

### 2.3 Transparent Intelligence
* All recommendations must be explainable
* No black-box ML in v1
* Scoring must be deterministic and tunable

### 2.4 Cross-Platform Awareness
* Android is built first
* All logic must be platform-agnostic where possible
* Avoid Android-specific assumptions in:
    * scoring logic
    * data model
    * behavior definitions

## 3. 🏗️ Technical Stack (Android v1)
* **Language:** Kotlin
* **UI:** Jetpack Compose
* **Audio:** Media3 (ExoPlayer)
* **Database:** Room
* **Background:** WorkManager

## 4. 📁 Repository Structure (Initial)
```text
Flyer/
  app/
    src/main/java/com/flyer/
      ui/
      playback/
      library/
      data/
      analytics/
      recommendations/
      sync/
```

## 5. 🎧 Playback System
**Responsibilities**
* Play local audio files
* Manage queue (play, pause, skip, shuffle)
* Background playback
* Notification + lock screen controls

**Core Components**
* `PlaybackService`
* `MediaSession`
* `QueueManager`

## 6. 📚 Library System
**Responsibilities**
* Scan device for audio files
* Parse metadata (title, artist, album, duration)
* Normalize metadata
* Detect duplicates

### 6.1 Canonical Track Model (Critical)
All scoring must occur at canonical track level, not file level.

**CanonicalTrack**
* id
* normalizedTitle
* normalizedArtist
* normalizedAlbum
* normalizedDurationMs

**TrackFile**
* id
* canonicalTrackId
* filePath
* bitrate
* format
* lastScannedAt

**Dedup Strategy (v1)**
* normalize strings (lowercase, trim, remove punctuation)
* match:
    * title
    * artist
    * duration (±2 seconds)
* group into canonical track

## 7. 📊 Behavior Tracking System
**Event Types**
* PLAY_START
* PLAY_COMPLETE
* SKIP_EARLY
* SKIP_MID
* SKIP_LATE
* REPLAY

**Thresholds (Percentage-Based)**
* Early Skip: < 20%
* Mid Skip: 20–60%
* Late Skip: > 60%
* Completion: ≥ 85%

**Data Model**
* `ListeningEvent` (id, canonicalTrackId, eventType, positionMs, timestamp)
* `TrackStats` (canonicalTrackId, totalPlays, fullPlays, earlySkips, replays, lastPlayedAt)

## 8. ❤️ User Feedback System
**UserFeedback**
* id
* canonicalTrackId
* type (LOVE, HIDE, NEVER_PLAY)
* timestamp

**Rules**
* NEVER_PLAY = absolute exclusion
* LOVE = strong boost
* HIDE = reduce frequency

## 9. 🧠 Ranking Engine
**Multi-Layer Model**
1. **Behavioral Score:** play count, completion rate, skip rate, replay rate, recency
2. **Explicit Score:** LOVE, HIDE, NEVER_PLAY
3. **Context Score:** time of day, recent plays (fatigue), session patterns

**Final Score**
`finalScore = behavioralScore + explicitScore + contextScore`

## 10. 🔀 Smart Shuffle (Core Feature)
**Goals**
* Favor high-affinity tracks
* Avoid repetition
* Include some exploration

**Algorithm (v1)**
* Remove NEVER_PLAY
* Reduce recently played tracks
* Weight by affinity score
* Randomized selection from weighted pool
* Inject occasional mid-affinity tracks

## 11. 🔌 Offline-First Boundaries
**Must Work Offline**
* playback
* library browsing
* stats
* smart shuffle
* user feedback

**Online (Future / Optional)**
* recommendations (external or crowd)
* concert data
* sync

## 12. 🌐 Future: Crowd Sync System (Planned)
**Opt-In Only**
Users may share:
* anonymized track identifiers
* affinity scores
* behavior summaries

**Must NOT Share**
* file paths
* personal identifiers
* raw listening history tied to identity

**Future Matching**
`userSimilarity = overlap(highAffinityArtists) + similarity(skipPatterns)`

## 13. 🧱 Database Entities (Core)
* `CanonicalTrack`
* `TrackFile`
* `ListeningEvent`
* `TrackStats`
* `ArtistStats` (future)
* `UserFeedback`

## 14. 🚀 Development Phases
* **Phase 1A — Foundation:** basic playback, file scan, canonical track system, event tracking
* **Phase 1B — Intelligence:** stats calculation, ranking engine, smart shuffle, insights screen
* **Phase 2 — Discovery:** local recommendation engine, optional crowd sync
* **Phase 3 — Expansion:** concerts, external integrations (optional)

## 15. 📱 Core Screens (v1)
* Home
* Library
* Now Playing
* Insights

## 16. ⚠️ Non-Goals (v1)
* streaming integration
* social features
* complex machine learning
* cloud dependency

## 17. 🍎 iOS Considerations (Future)
**Must Stay Platform-Agnostic**
* scoring formulas
* behavior definitions
* database structure concepts
* recommendation logic

**Will Be Platform-Specific**
* playback engine (Media3 → AVFoundation)
* UI (Compose → SwiftUI)
* file access
* background audio handling

## 18. 🧭 Guiding Principle
The app should feel smarter after a week of use.

## 🧭 19. AI Agent Workflow
(See GEMINI_WORKFLOW.md for details)
