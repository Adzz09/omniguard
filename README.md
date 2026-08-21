# OmniGuard: Next-Generation Personal Safety & Emergency Escalation Platform

OmniGuard is an intelligent personal safety and emergency escalation platform engineered with **Kotlin Multiplatform**, **Jetpack Compose / Wear OS**, and a high-performance **Ktor 3.x Backend Server**. OmniGuard delivers automated fall & crash detection, covert duress dispatch, intelligent safe corridor routing, schedule-aware geofencing, and zero-install live web tracking for trusted emergency contacts.

---

## 🏛 Architecture & Modular Topology

OmniGuard is organized as a modular Kotlin/Android multi-module architecture following Clean Architecture principles:

```
omniguard/
├── core/
│   ├── model/           # Shared domain entities (UserRole, EmergencyContact, FallIncident, SafeZone, TransitLog, SOSState)
│   ├── data/            # Repositories, AES-256-GCM encryption engine, 7-day data retention purge lifecycle
│   └── network/         # Ktor client network layer, WebSocket client connectors
├── feature/
│   ├── falldetection/   # 60s-120s countdown timer, peak-G sensor processor, ambient audio recorder, escalation coordinator
│   ├── guidemehome/     # Schedule-aware geofencing (ScheduleGeofenceManager), SafeRouteResolver (well-lit/CCTV scoring)
│   ├── sos/             # Covert duress PIN manager, rapid triple-press panic button detector, emergency dispatch
│   ├── geofencing/      # Encrypted transit logs (LogEncryptor) & silent watch haptic cues (WatchHapticNotifier)
│   └── onboarding/      # Role preset selection (Biker, Student, Elderly) & emergency contact setup
├── backend-server/      # Ktor 3.x application with WebSockets, Leaflet/OSM Live Tracking Web Viewer, SMS/FCM engine
├── app-android/         # Jetpack Compose mobile application
└── app-wear/            # Wear OS Compose wearable application
```

---

## 🚀 Backend Server (`:backend-server`)

The `:backend-server` module is powered by **Ktor 3.x (CIO engine)** and handles emergency escalation, real-time WebSocket distribution, and passive geofencing notifications.

### Key Capabilities

1. **Emergency Event Escalation Engine**:
   - `POST /api/v1/emergency/sos` — Ingests silent SOS, covert duress, or fall escalation payloads; initializes tracking session token and returns real-time tracking URL.
   - `POST /api/v1/emergency/cancel` — Validates cancellation requests during the grace period, notifying connected contacts and logging cancellation source (Hardware crown, touchscreen PIN, voice).
   - `POST /api/v1/tracking/{sessionId}/ping` — Ingests high-frequency GPS telemetry packets (`lat`, `lng`, `speed`, `altitude`, `batteryPercent`) and streams to active subscribers.
   - `GET /api/v1/tracking/{sessionId}` — Returns JSON metadata snapshot and breadcrumb history for a tracking session.
   - `WS /api/v1/tracking/{sessionId}` — Real-time reactive WebSocket stream broadcasting live `LOCATION_UPDATE`, `STATUS_CHANGE`, and `CANCELLED` packets.

2. **Passive Geofencing & Simulated Notification Dispatch**:
   - `POST /api/v1/geofence/ping` — Ingests schedule-aware safe zone arrival/departure transitions. Automatically dispatches simulated **Twilio SMS** and **Firebase Cloud Messaging (FCM)** alerts to trusted emergency contacts.

3. **Live Tracking HTML Web Viewer (FR-03 Compliance)**:
   - `GET /live/{sessionId}` and `GET /api/v1/tracking/{sessionId}/viewer`
   - Zero-install web viewer with **Leaflet.js** and **OpenStreetMap/CartoDB Dark Matter tiles**.
   - Displays:
     - Real-time animated pulsating avatar marker.
     - Dynamic polyline breadcrumb trail tracing past waypoints.
     - User role indicators: 🏍️ *Biker*, 🎓 *Student*, 👵 *Elderly*.
     - Live telemetry HUD: coordinates, speed (km/h), battery gauge, accuracy perimeter.
     - Native browser WebSocket client for sub-second position updates and cancellation notifications.

---

## 🧪 Comprehensive Test Suite

OmniGuard features a robust automated test suite utilizing **JUnit 5**, **Kotlin Test**, **MockK**, and **CashApp Turbine** for coroutine and reactive `StateFlow` / `SharedFlow` testing.

### Test Coverage Summary

| Module | Test File | Key Test Cases Covered |
| :--- | :--- | :--- |
| `:core:data` | [`AesEncryptionEngineTest.kt`](file:///D:/mayukh-is-a-donkey/omniguard/core/data/src/test/kotlin/com/omniguard/core/data/crypto/AesEncryptionEngineTest.kt) | AES-256-GCM encryption/decryption roundtrip, random IV generation per payload, corrupted ciphertext detection. |
| `:core:data` | [`EmergencyContactRepositoryTest.kt`](file:///D:/mayukh-is-a-donkey/omniguard/core/data/src/test/kotlin/com/omniguard/core/data/repository/EmergencyContactRepositoryTest.kt) | **FR-01** max 5 contacts enforcement, contact CRUD operations, reactive `contactsFlow` emission via Turbine, notification flag toggling. |
| `:core:data` | [`TransitLogPurgeTest.kt`](file:///D:/mayukh-is-a-donkey/omniguard/core/data/src/test/kotlin/com/omniguard/core/data/repository/TransitLogPurgeTest.kt) | **7-day automatic data purge verification** — verifies logs older than 7 days are automatically pruned while preserving recent logs, with reactive `logsFlow` updates. |
| `:feature:falldetection` | [`FallCountdownTimerTest.kt`](file:///D:/mayukh-is-a-donkey/omniguard/feature/falldetection/src/test/java/com/omniguard/feature/falldetection/timer/FallCountdownTimerTest.kt) | 60s countdown initialization, decrementing progress with `TestScope` virtual time advance, critical warning trigger when $\le 15$s, user cancellation abort, and `onComplete` escalation callback execution. |
| `:feature:falldetection` | [`FallEscalationManagerTest.kt`](file:///D:/mayukh-is-a-donkey/omniguard/feature/falldetection/src/test/java/com/omniguard/feature/falldetection/escalation/FallEscalationManagerTest.kt) | MockK-based verification aggregating GPS snapshot, 10s ambient audio recording metadata, dispatch service invocation, and shared flow emission. |
| `:feature:guidemehome` | [`ScheduleGeofenceTest.kt`](file:///D:/mayukh-is-a-donkey/omniguard/feature/guidemehome/src/test/java/com/omniguard/feature/guidemehome/tracking/ScheduleGeofenceTest.kt) | Normal and over-midnight `ScheduleWindow` evaluation, Haversine radius boundary detection, reactive `SAFE_ZONE_ENTER` / `SAFE_ZONE_EXIT` flow emissions via Turbine, and inactive schedule suppression. |
| `:feature:guidemehome` | [`SafeRouteResolverTest.kt`](file:///D:/mayukh-is-a-donkey/omniguard/feature/guidemehome/src/test/java/com/omniguard/feature/guidemehome/router/SafeRouteResolverTest.kt) | Intelligent safe route scoring favoring well-lit streets with CCTV and foot traffic over unlit isolated shortcuts. |
| `:feature:sos` | [`DuressPinTest.kt`](file:///D:/mayukh-is-a-donkey/omniguard/feature/sos/src/test/java/com/omniguard/feature/sos/pin/DuressPinTest.kt) | Real PIN vs Covert Duress PIN validation, stealth `SOSEmergencyEvent` dispatch with `isSilentDuress = true`, lockout after max failed attempts tested via Turbine. |
| `:feature:sos` | [`SOSPanicManagerTest.kt`](file:///D:/mayukh-is-a-donkey/omniguard/feature/sos/src/test/java/com/omniguard/feature/sos/panic/SOSPanicManagerTest.kt) | Wearable/mobile rapid triple-press detection within 1500ms triggering SOS event flow, slow press rejection. |
| `:backend-server` | [`EmergencyEscalationServerTest.kt`](file:///D:/mayukh-is-a-donkey/omniguard/backend-server/src/test/kotlin/com/omniguard/backend/EmergencyEscalationServerTest.kt) | End-to-end Ktor server test application verifying `POST /api/v1/emergency/sos`, `GET /api/v1/tracking/{id}`, `POST /api/v1/tracking/{id}/ping`, `POST /api/v1/emergency/cancel`, `POST /api/v1/geofence/ping`, and `GET /live/{id}` HTML rendering. |
| `:backend-server` | [`WebSocketLiveTrackingTest.kt`](file:///D:/mayukh-is-a-donkey/omniguard/backend-server/src/test/kotlin/com/omniguard/backend/WebSocketLiveTrackingTest.kt) | Full duplex WebSocket streaming verification — client connects, receives `INITIAL_STATE`, receives live `LOCATION_UPDATE` when new coordinate ping arrives, and receives `CANCELLED` frame upon abort. |

---

## 📋 PRD Compliance Matrix

| Requirement | Description | Implementation Details |
| :--- | :--- | :--- |
| **FR-01** | Max 5 Verified Emergency Contacts | Enforced in `DefaultEmergencyContactRepository` with `MAX_CONTACTS_ALLOWED = 5` and unit-tested in `EmergencyContactRepositoryTest`. |
| **FR-02** | Configurable Fall Detection Sensitivity | Handled across `UserRole` presets (`BIKER`: High/15s, `STUDENT`: Medium/20s, `ELDERLY`: Maximum/30s) and `FallCountdownTimer`. |
| **FR-03** | Zero-Install Browser Live Tracking | Embedded Leaflet.js HTML web viewer rendered via Ktor at `GET /live/{sessionId}` and live-updated over WebSockets. |
| **FR-04** | Covert Duress Alarm | Implemented via `DuressPinManager` offering alternate PIN verification with stealth background SOS dispatch. |
| **FR-05** | Schedule-Aware Geofencing | `ScheduleGeofenceManager` and `ScheduleWindow` supporting custom active days and over-midnight time intervals. |
| **FR-06** | 7-Day Local Data Retention & Privacy | AES-256-GCM encryption with `AesEncryptionEngine` and automatic 7-day transit log pruning in `TransitLogRepository`. |

---

## 🛠 Running the Application & Tests

### 1. Running the Ktor Backend Server
To launch the backend server locally on port 8080:
```bash
./gradlew :backend-server:run
```
Once started, visit:
- **Server Health Check**: `http://localhost:8080/`
- **Live Tracking Web Viewer**: `http://localhost:8080/live/{sessionId}`

### 2. Running All Unit & Flow Test Suites
To run all tests across all modules with JUnit 5:
```bash
./gradlew test
```

To run module-specific test suites:
```bash
# Core Data Tests (AES-256-GCM, Contacts, 7-day log purge)
./gradlew :core:data:test

# Fall Detection & Timer Tests
./gradlew :feature:falldetection:test

# Safe Route & Schedule Geofence Tests
./gradlew :feature:guidemehome:test

# SOS & Duress PIN Tests
./gradlew :feature:sos:test

# Backend Server & WebSocket Live Streaming Tests
./gradlew :backend-server:test
```
