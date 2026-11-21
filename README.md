# StudyFlow — Student Productivity & AI Study Companion

**StudyFlow** (`com.studyflow.student`, v1.0.0) — *Study smarter. Stay organized. Achieve more.*

StudyFlow is a complete, offline-first Android productivity and learning application built with **Kotlin**, **Jetpack Compose**, **Material 3**, **Room**, **DataStore**, **WorkManager**, **Firebase Authentication (Google Sign-In via Credential Manager)**, **Cloud Firestore**, and **Firebase AI Logic (Gemini)**.

---

## Core Modules

1. **Home Dashboard**
   - Personalized greeting, current date, motivational message, and steady study streak badge.
   - Customizable and reorderable cards: Today's Timetable, Pending Homework, Upcoming Exams, Today's Reminders, Daily Goals, Study-Time & Progress (Compose Canvas charts), and Revision Progress.
   - Global Quick-Add button, Global Search across all academic items, and Notification Center.

2. **Planner (Homework, Timetable, Exams & Calendar)**
   - **Homework Manager**: Pending / Overdue / Completed tabs, search, subject & priority filters, deadline sorting, image attachments (Android Photo Picker), document attachments (SAF `OpenDocument`), deadline countdowns, auto-reminders, and completion stats.
   - **Study Timetable**: Daily & weekly views, class/study/break sessions, live overlap/conflict detection, and free-time window finder.
   - **Exams & Syllabus**: Exam countdowns, chapter-wise syllabus checklists, difficult-topic flagging, preparation status, and mock test score tracking.
   - **Academic Calendar**: Unified agenda view aggregating deadlines, exams, scheduled revisions, and custom events with category filters.

3. **Smart Reminder System & Rescheduling**
   - Custom, homework, assignment, exam, study session, revision, and class reminders with priority levels, advance alerts, daily/weekly/custom-day repeats, snooze, completion, and missed-reminder history.
   - **Smart Rescheduling**: Detects missed reminders/study sessions and proposes conflict-free catch-up slots based on the student's timetable and preferred study hours.
   - **Android Reliability**: Requests `POST_NOTIFICATIONS` on Android 13+, checks `canScheduleExactAlarms()` on Android 12+/14+ with an explicit settings shortcut and inexact fallback, restores alarms on reboot / time / timezone changes via `BootAndTimeReceiver`, and honestly explains OEM battery optimization limitations.

4. **Study Studio (Timer, Notes, Revisions, Flashcards, Quizzes, AI Tutor, Progress & Attendance)**
   - **Focus Timer**: Pomodoro (25m), Custom Focus (10–120m), Short Break, and Long Break with an animated Canvas dial, subject tagging, completion notifications, and session history.
   - **Notes Manager & Smart Revision**: Offline subject/chapter notes with formatting helpers, bookmarks, important flags, Photo Picker images, plus spaced-repetition revision scheduling.
   - **Flashcards & Quizzes**: Interactive flip-card deck, offline deck-based MCQ quizzes, quiz history, and 1-tap mistake explanations.
   - **AI Study Companion (Firebase AI Logic)**: Step-by-step concept tutor, note summarizer, structured flashcard generator, interactive practice quiz generator, and exam revision planner adapted to the student's grade level—with educational safety instructions, daily usage tracking, and clear accuracy disclaimers. No Gemini API keys are stored in the client app.
   - **Progress & Attendance**: Real calculated focus minutes (today / 7d / 30d), 7-day Compose Canvas bar chart, subject-wise Present/Absent attendance tracking with low-attendance alerts, supportive streaks/achievements, and CSV Academic Report export.

5. **Profile, Private Study Groups, Security & Backup**
   - **Private Study Groups**: Create private groups with 6-character invite codes, join classmates via invite code, and collaborate on shared goals, notes, tasks, and quiz challenges backed by zero-trust Firestore rules.
   - **Cloud Sync & Backup**: Offline-first Room persistence with conflict-safe Cloud Firestore sync (local data is never erased on sync failure), Offline Mode toggle, and local JSON export/restore.
   - **Themes & Security**: System / Light / Dark modes, 4 accent palettes, salted SHA-256 App PIN lock, optional `BiometricPrompt` unlock, Help/FAQ, Privacy Policy, Feedback, and full Account Data Deletion.

---

## Architecture & Tech Stack

- **UI**: Jetpack Compose + Material 3, adaptive `NavigationBar` (phones) & `NavigationRail` (tablets/foldables), custom bundled typography (`Outfit`, `Plus Jakarta Sans`, `JetBrains Mono`), adaptive launcher icon, and `AppWidgetProvider` home-screen widget.
- **Architecture**: MVVM + Repository pattern with Type-Safe `@Serializable` Navigation Compose routes.
- **Local Persistence**: Room (17 entities + DAOs), DataStore Preferences, WorkManager (`MissedReminderScanWorker`, `CloudSyncWorker`).
- **Cloud Backend**: Firebase Authentication (Google Sign-In via Jetpack Credential Manager), Cloud Firestore (`firestore.rules`), Firebase App Check (`DebugAppCheckProviderFactory` in debug / `RecaptchaAppCheckProviderFactory` in release), and Firebase AI Logic (`Firebase.ai`).

---

## Build, Test & APK Instructions

### Prerequisites
- JDK 17+ / Gradle 9.x (`gradle` CLI)
- Android SDK 36 (`ANDROID_SDK_ROOT` configured)
- Firebase configuration (`app/google-services.json` and `R.string.firestore_database_id` provisioned for the project)

### Common Commands
```bash
# Compile Kotlin sources
gradle :app:compileDebugKotlin

# Run local JVM & Robolectric unit tests
gradle :app:testDebugUnitTest

# Build Debug APK
gradle :app:assembleDebug
```

### Installing the APK
- The verified debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk` and copied to the project root as `StudyFlow-v1.0.0.apk`.
- Transfer `StudyFlow-v1.0.0.apk` to an Android device (Android 7.0+ / API 24+) or install via Android Studio / `adb install StudyFlow-v1.0.0.apk`.

### Release Signing Notes
To build a signed release APK (`gradle :app:assembleRelease`), generate your own upload keystore and supply credentials via environment variables (`KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`) with key alias `upload`. Never commit private keystores or signing passwords to source control.
