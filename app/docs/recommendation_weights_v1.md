Here’s a repo-ready draft for `docs/recommendation_weights_v1.md`.

````md
# Recommendation Weights v1

## Purpose

This document defines the first scoring model for Flyer’s local recommendation and smart shuffle system.

The goal is to produce recommendations that are:

- deterministic
- tunable
- explainable
- offline-first
- platform-agnostic

This model is intended for Phase 1B and should build directly on the Phase 1A data model and event tracking foundation. The design follows Flyer’s broader philosophy that the app should learn from behavior first, remain understandable, and avoid black-box logic in v1. :contentReference[oaicite:0]{index=0}

---

## Guiding Principles

### 1. Behavior is the primary signal

Listening behavior should matter more than manual playlist curation alone.

Primary signals include:

- plays
- completions
- skips
- replays

This follows the product philosophy that Flyer learns from what the user actually does, not just what they say they like. :contentReference[oaicite:1]{index=1}

### 2. Explicit feedback must be respected

Explicit user feedback should be strong and predictable.

- `LOVE` should meaningfully boost a track
- `HIDE` should reduce frequency
- `NEVER_PLAY` should exclude a track entirely

### 3. Context should guide, not dominate

Context should influence selection lightly in v1.

Examples:

- time of day
- recent session patterns
- recent repetition fatigue

### 4. The score must be explainable

Every score should be decomposable into understandable parts.

Example explanation:

> “This track ranked highly because you finish it often, replay it occasionally, and marked it as loved.”

### 5. Keep logic platform-agnostic

Scoring formulas should not depend on Android-only concepts so they can later be reused for iOS.

This matches the design requirement that recommendation logic remain portable even if playback and UI layers become platform-specific later. :contentReference[oaicite:2]{index=2}

---

## Score Overview

Flyer v1 uses three score groups:

```text
finalScore = behavioralScore + explicitScore + contextScore
````

Recommended v1 weighting:

* Behavioral Score: **60 points**
* Explicit Score: **30 points**
* Context Score: **10 points**

This keeps behavior as the main engine, gives strong authority to explicit feedback, and allows context to act as a lightweight modifier.

---

## Hard Exclusion Rule

Before any weighting is applied:

```text
if feedback == NEVER_PLAY -> exclude track
```

`NEVER_PLAY` is not a penalty. It is a hard filter. This matches the core rule in the design document.

---

## Behavioral Score

Behavioral score ranges from **0 to 60**, with one penalty component that can reduce the total.

### Components

* Completion Score: `0 to 20`
* Replay Score: `0 to 15`
* Play Count Score: `0 to 10`
* Recency Score: `0 to 15`
* Skip Penalty: `-20 to 0`

### Formula

```text
behavioralScore =
    completionScore
  + replayScore
  + playCountScore
  + recencyScore
  + skipPenalty
```

---

## 1. Completion Score

Completion is one of the strongest positive signals.

The design doc defines completion as playback reaching at least 85% of the track.

### Formula

```text
completionRate = fullPlays / max(totalPlays, 1)
completionScore = completionRate * 20
```

### Notes

* A track frequently played to completion should rank well
* Completion should matter more than raw play count
* This helps distinguish real favorites from background noise

---

## 2. Replay Score

Replay is a strong signal of affinity.

A replay usually means the user actively wanted more of that track.

### Formula

```text
replayRate = replays / max(totalPlays, 1)
replayScore = min(replayRate, 1.0) * 15
```

### Notes

* Cap at 15 to prevent outlier behavior from distorting results
* Replays should be stronger than a normal play
* Replay should generally be treated as one of the best positive signals

---

## 3. Play Count Score

Play count matters, but should have diminishing returns.

Without a cap or curve, raw play count can dominate the system and trap the app in old listening habits.

### Formula

```text
playCountScore = min(log10(totalPlays + 1) / 2.0, 1.0) * 10
```

### Notes

* Gives value to familiarity
* Prevents extremely high play counts from overpowering other signals
* Keeps the model from overfitting to the oldest favorites

---

## 4. Recency Score

Recent engagement should matter.

A track loved recently should typically be ranked higher than one not played in months, unless the older track has significantly stronger long-term behavior.

### v1 Bucketed Formula

```text
played within 1 day      -> 15
played within 3 days     -> 12
played within 7 days     -> 8
played within 14 days    -> 5
played within 30 days    -> 2
older than 30 days       -> 0
```

### Notes

* Buckets are easier to reason about than a more complex decay formula
* This is deterministic and easy to debug
* Recency should boost relevance but not erase long-term affinity

---

## 5. Skip Penalty

Skips are important negative signals, but not all skips mean the same thing.

The design doc defines:

* Early Skip: `< 20%`
* Mid Skip: `20% to 60%`
* Late Skip: `> 60%`

### Penalty Weights

* Early Skip: `1.0`
* Mid Skip: `0.5`
* Late Skip: `0.15`

### Formula

```text
rawSkipPenalty =
    (earlySkips * 1.0)
  + (midSkips * 0.5)
  + (lateSkips * 0.15)

skipPenalty = -min(rawSkipPenalty, 20)
```

### Notes

* Early skips should hurt much more than late skips
* A late skip often means “done for now,” not necessarily dislike
* Penalty is capped so a track is not permanently destroyed by older behavior

---

## Explicit Score

Explicit score ranges from **-12 to +25** in v1.

### Formula

```text
explicitScore =
    loveBoost
  + hidePenalty
```

### Weights

* `LOVE` = `+25`
* `HIDE` = `-12`
* `NEVER_PLAY` = excluded before scoring

### Notes

* Explicit feedback should be stronger than ambiguous behavioral signals
* `LOVE` should clearly elevate a track
* `HIDE` should reduce resurfacing frequency without making recovery impossible
* `NEVER_PLAY` remains absolute exclusion

---

## Context Score

Context score ranges from approximately **-9 to +7** in v1, but is conceptually budgeted as a lightweight 10-point influence.

### Formula

```text
contextScore =
    timeOfDayScore
  + sessionMatchScore
  + fatiguePenalty
```

### Recommended Weights

#### Time of Day Score

```text
strong match   -> +4
weak match     -> +2
neutral        -> 0
```

Examples:

* user often completes this track in morning sessions
* user often skips it at night

#### Session Match Score

```text
good session fit -> +3
neutral          -> 0
```

Examples:

* same artist cluster as current listening streak
* similar energy to recent completed tracks
* similar genre cluster if local classification exists

#### Fatigue Penalty

```text
played within 30 minutes -> -7
played within 2 hours    -> -5
played today             -> -3
otherwise                -> 0
```

### Notes

* Context should help selection feel smart in the moment
* Context should not completely override long-term preference
* Fatigue is especially important for smart shuffle

---

## Final Score Formula

```text
if feedback == NEVER_PLAY:
    exclude

finalScore =
    completionScore
  + replayScore
  + playCountScore
  + recencyScore
  + skipPenalty
  + explicitScore
  + contextScore
```

---

## Example Scoring

### Example A: Strong Favorite

Track stats:

* totalPlays = 20
* fullPlays = 14
* replays = 4
* earlySkips = 1
* midSkips = 0
* lateSkips = 1
* lastPlayedAt = yesterday
* feedback = LOVE
* timeOfDayMatch = weak
* sessionMatch = neutral
* fatigue = not recent

Approximate result:

```text
completionScore = 14 / 20 * 20 = 14
replayScore = 4 / 20 * 15 = 3
playCountScore ≈ 6
recencyScore = 15
skipPenalty = -(1.0 + 0.15) = -1.15
explicitScore = +25
contextScore = +2

finalScore ≈ 63.85
```

Interpretation:

* Strong finisher
* Positive replay signal
* Recently relevant
* Explicitly loved

This should rank high.

---

### Example B: Frequently Rejected Track

Track stats:

* totalPlays = 10
* fullPlays = 2
* replays = 0
* earlySkips = 5
* midSkips = 1
* lateSkips = 0
* lastPlayedAt = today
* feedback = none
* fatigue = played today

Approximate result:

```text
completionScore = 2 / 10 * 20 = 4
replayScore = 0
playCountScore ≈ 5
recencyScore = 15
skipPenalty = -(5 * 1.0 + 1 * 0.5) = -5.5
explicitScore = 0
contextScore = -3

finalScore ≈ 15.5
```

Interpretation:

* Recently played, but behavior is weak
* Heavy early skipping strongly suppresses it

This should rank low.

---

## Smart Shuffle Relationship

This score is an **affinity score**, not the entire shuffle algorithm.

Smart shuffle should use `finalScore` as a major input, but should also apply additional selection rules such as:

* remove `NEVER_PLAY`
* reduce recently played tracks
* avoid repeated artists/albums in short windows
* inject occasional mid-affinity exploration
* preserve some randomness

This matches the design doc’s distinction between affinity and smart shuffle behavior.

---

## Suggested Implementation Notes

### Data Requirements

This model assumes the app can read from local entities such as:

* `ListeningEvent`
* `TrackStats`
* `UserFeedback`

Those entities are already part of the core design direction for Flyer.

### Recommended Separation

Keep the implementation split into:

1. **Stats aggregation**

    * converts events into track-level metrics

2. **Scoring engine**

    * computes component scores and final score

3. **Selection engine**

    * uses score plus anti-repetition rules to choose playback order

This separation aligns with Flyer’s preference for clarity, small composables, clean data flow, and logic outside UI code.

---

## Explainability Output

The scoring system should be able to return both:

* `finalScore`
* a component breakdown

Suggested shape:

```text
TrackRecommendationBreakdown
- canonicalTrackId
- finalScore
- completionScore
- replayScore
- playCountScore
- recencyScore
- skipPenalty
- explicitScore
- contextScore
- explanationSummary
```

Example explanation summary:

> “High score due to strong completion rate, recent playback, and LOVE feedback.”

This supports the design goal that recommendations be transparent and explainable.

---

## Tuning Rules

When adjusting weights later, follow these priorities:

1. Keep behavior as the main signal
2. Keep explicit feedback strong
3. Keep context lightweight
4. Avoid letting play count dominate
5. Penalize early skips much more than late skips
6. Preserve deterministic behavior

If tuning changes are made, record:

* what changed
* why it changed
* what user behavior it is meant to improve

This matches the repo’s change guidance to be explicit about behavior changes and assumptions.

---

## Known Future Extensions

These are intentionally out of scope for v1, but can be added later:

* artist-level affinity adjustments
* album sequencing preferences
* novelty or exploration boost
* seasonal or weekday patterns
* crowd-sourced similarity signals
* lightweight on-device clustering
* negative decay on stale old favorites

These should only be added after validating that the simpler v1 model feels good in practice.

---

## v1 Recommendation

Adopt this weighting set as the initial baseline:

```text
Behavioral:
- Completion: 20
- Replay: 15
- Play Count: 10
- Recency: 15
- Skip Penalty: up to -20

Explicit:
- LOVE: +25
- HIDE: -12
- NEVER_PLAY: exclude

Context:
- Time of Day: up to +4
- Session Match: up to +3
- Fatigue: down to -7
```

This gives Flyer a first recommendation model that is:

* simple
* inspectable
* explainable
* tunable
* fully compatible with offline-first behavior
* suitable for both ranking and smart shuffle input

```

A couple of tuning presets you may want later:

- **Comfort mode**: stronger play-count and recency, less exploration
- **Discovery mode**: slightly weaker play-count, stronger replay and lower fatigue penalties
- **Fresh ears mode**: stronger fatigue penalties and more mid-affinity injection

Next best move is probably a second doc: `docs/smart_shuffle_v1.md`, so the scoring model and selection model stay separate instead of becoming headphone spaghetti.
```
