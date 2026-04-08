# GEMINI_WORKFLOW.md

## Overview
Flyer uses a multi-agent development model to ensure consistency, architectural integrity, and efficiency. This document defines the specific role and boundaries for **Gemini** within Android Studio.

## 🧠 Core Principle
The closer the work is to the code editor, the more Gemini should handle it. The farther the work is from the code (design, architecture), the more it should be handled by Claude or ChatGPT.

## ⚙️ Gemini Responsibilities (Primary)
Gemini is the default tool inside Android Studio for the "inner loop" of development.

### 🧱 Implementation Tasks
* Creating Kotlin classes
* Building Compose UI
* Wiring Media3 playback
* Implementing Room entities and DAOs
* Adding WorkManager jobs

### 🔧 Debugging
* Gradle errors and build failures
* Dependency issues
* Logcat crashes and runtime exceptions

### 🧹 Refactoring
* Breaking large composables into smaller ones
* Renaming for clarity
* Extracting interfaces
* Improving readability

### 📝 Documentation
* KDoc generation
* Inline explanations

## 🚫 Gemini Constraints
Gemini should **NOT** perform the following without explicit human instruction or a pre-approved architectural plan:
* Redefine architecture
* Change data models substantially
* Introduce new frameworks
* Add backend systems (Cloudflare/Supabase)
* Modify core scoring logic
* Introduce cloud dependencies

## 🧪 Gemini Agent Mode Rules
* **Allowed:** Scoped feature implementation, adding a single screen, wiring a known component.
* **Not Allowed:** Building entire systems at once, restructuring the project, introducing new architecture patterns.
* **Mandatory:** Always review diffs before accepting changes.

## 🔁 Workflow Loop
1. **Define Task (Claude/ChatGPT):** Define the "what" and "why".
2. **Implement (Gemini):** Perform the "how" inside Android Studio.
3. **Review:** Check diffs, run the app, and verify behavior.
4. **Iterate:** Fix issues with Gemini or escalate back to Claude for complex structural changes.

## ⚖️ Decision Hierarchy
When conflicts arise, follow this order:
1. DESIGN.md
2. ARCHITECTURE.md
3. AGENT.md
4. Human instruction

Gemini must not override these documents.
