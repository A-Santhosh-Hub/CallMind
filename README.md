<p align="center">
  <img src="assets/icon.png" alt="CallMind icon" width="120" />
</p>

<h1 align="center">CallMind</h1>

<p align="center"><b>Your calls. Your memory. Your next move.</b></p>

<p align="center">
  An Android communication companion that remembers every call, and can also replace your phone app.<br/>
  Developed by <a href="https://sanstudio-hub.github.io/in/"><b>SanStudio</b></a>
</p>

<p align="center">
  <img alt="Platform" src="https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84?logo=android&logoColor=white" />
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.1-7F52FF?logo=kotlin&logoColor=white" />
  <img alt="Jetpack Compose" src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white" />
  <img alt="Version" src="https://img.shields.io/badge/version-2.0-blue" />
</p>

---

## What is CallMind?

Most caller apps answer one question: *"Who is calling?"*
CallMind answers the next one: **"What should I do after this call?"**

It sits next to your normal phone app and turns every call into something you can act on:
add a note, set a reminder, send a quick reply, jump to WhatsApp or Telegram, or see the full history with that person, all stored **on your phone only**.

For people who want one app for everything, CallMind also has a **Phone mode** that makes it your
real phone app: dial pad, recent calls, a full in-call screen, add call, hold, merge and more.

## Two modes, one switch

| | **Office mode** (default) | **Phone mode** |
|---|---|---|
| Who makes and receives calls | Your phone's own dialer | CallMind |
| Home screen | Dashboard, notes, reminders, history | Clean dial pad and recent calls |
| After a call | Post-call card with notes, reminders, quick actions | Same card, optional |
| Turn it on | Nothing to do | Settings, then **Use CallMind as my phone app** |

Switching back to Office mode hands calls back to your phone's own dialer automatically.

## Features

### Call memory (Office mode)
- **Post-call card**: after each call, see who it was and add a note, schedule a reminder, call back, or open WhatsApp or Telegram in one tap.
- **Notes**: attach notes to a person or a specific call, and search them later.
- **Reminders**: schedule a call-back; reminders fire even after a reboot (WorkManager).
- **Quick replies**: saved message templates you can send to the caller in one tap.
- **Call blocking**: block list and trust list, optionally block unknown numbers or unsaved contacts, with a history of what was blocked.
- **Contacts**: browse, search, favourites, alphabet fast-scroll, and create or edit contacts.
- **Call history**: calls grouped by person, with the notes and reminders for each.
- **Global search** across contacts, calls, notes and reminders.
- **Light and dark themes**, onboarding, and a permission flow that explains each permission before asking.

### Phone mode (CallMind as your phone app)
- **Dial pad** with T9 smart search (matches names and initials), hides itself when you scroll the list.
- **Recent calls** with tabs, search, multi-select delete, long-press menu and SIM information.
- **Call details**: full call log per number, notes, and recording playback.
- **Incoming call screen** with swipe to answer, decline with a message, and a heads-up notification card when you are in another app.
- **In-call screen**: mute, speaker and Bluetooth routing, keypad (DTMF), hold, **add call**, merge, swap, and a proximity sensor that turns the screen off at your ear.
- **Dual SIM** support, voicemail long-press on key 1, and Wi-Fi calling / HD call indicators.
- **Call recordings screen**: lists and plays recordings made by the phone's own call recorder, linked to the right call and contact.

## Advantages

- **Private by design.** All notes, reminders and history live in a local Room database. No account, no server, no analytics, no ads.
- **Works with or without replacing your dialer.** Use it as a companion, or go all in with Phone mode.
- **Fast and light.** The release APK is under 2 MB (R8 shrinking), and the UI is fully Jetpack Compose.
- **Modern stack.** Kotlin, Compose, Hilt, Room, WorkManager, Navigation, Coil, with unit tests for the T9 matcher, call blocking rules and recording matching.
- **Honest permissions.** Every permission is requested only when the feature needs it, with a plain explanation.
- **Easy to extend.** Clear feature-based package layout (`features/`, `core/`, `data/`).

## Disadvantages and known limitations

- **CallMind cannot record calls by itself on most modern phones.** Android only lets system apps capture the other person's voice, and many phones silence any other app's microphone during a call. CallMind tries once, detects silence, and tells you instead of saving an empty file. The workaround is to use your phone's own call recorder (in Office mode) and let CallMind list and play those recordings.
- **Phone mode and call recording exclude each other** on phones like vivo, where the built-in recorder only works with the built-in dialer.
- **Phone mode needs the default phone app role**, which Android may reset when you change the setting or after some system updates.
- **Tested mainly on one device** (vivo V2443). Behaviour of the in-call screen and the call-recording folder can differ on other manufacturers' phones.
- **Not Play Store ready.** The release APK is signed with the debug key, and apps that request call-log and default-dialer access need Google Play's declaration review.
- **Android only**, Android 8.0 (API 26) and newer.
- **Recording laws vary.** Where recording is possible, you are responsible for getting consent.

## Tech stack

| Area | Library |
|---|---|
| Language | Kotlin 2.1 |
| UI | Jetpack Compose, Material 3 |
| Architecture | MVVM, Kotlin Coroutines and Flow |
| Dependency injection | Hilt (KSP) |
| Database | Room (schema v6, with migrations) |
| Background work | WorkManager |
| Navigation | Navigation Compose |
| Images | Coil |
| Telephony | `InCallService`, `RoleManager`, Telecom, `CallScreeningService` |
| Build | Gradle 9.4, AGP 8.8, `minSdk 26`, `targetSdk 36` |

## Project structure

```
app/src/main/java/com/example/callmind/
├── core/          blocking, phone (in-call, recording, T9), telephony, notifications, permissions
├── data/          Room database, DAOs, entities, repositories, system providers
├── di/            Hilt modules
├── features/      calls, contacts, home, notes, reminders, quickreply, blocking,
│                  search, settings, onboarding, postcall, phone (dialer + in-call UI)
├── navigation/    app navigation graph
└── ui/            theme and shared components
```

## Download and install

**Just want to try it?** Install the ready-made APK: [`apk/CallMind-v2.0.apk`](apk/CallMind-v2.0.apk)
(allow "Install unknown apps" on your phone when asked).

## Build from source

1. Install **Android Studio Ladybug (2024.2) or newer** (it ships with JDK 21).
2. Clone the repository:
   ```bash
   git clone https://github.com/A-Santhosh-Hub/CallMind.git
   ```
3. Open the folder in Android Studio and wait for **Gradle sync** to finish.
4. Connect a phone (or start an emulator) and press **Run**.

No API keys or extra configuration are needed. To build the optimised release APK:

```bash
./gradlew assembleRelease      # Windows: gradlew.bat assembleRelease
```

Run the unit tests with `./gradlew testDebugUnitTest`.

## Permissions

| Permission | Why |
|---|---|
| Phone state, call log | Detect calls and show history |
| Contacts (read, write) | Show names, create and edit contacts |
| Notifications | Reminders and post-call card |
| Call phone | Place calls in Phone mode |
| Write call log | Delete entries from recents |
| Microphone | Optional in-app call recording |
| Music and audio | List recordings made by the phone's recorder |
| Full-screen intent | Show the incoming call screen over other apps |

## Repository details (copy these into GitHub)

**Repository name:** `CallMind`

**Short description (About box):**
> Android call companion and phone app built with Kotlin and Jetpack Compose: post-call notes, reminders, quick replies, call blocking, plus a full dialer and in-call screen. Private and offline.

**Topics / hashtags:**
`android` `kotlin` `jetpack-compose` `material3` `mvvm` `hilt` `room-database` `workmanager` `dialer` `phone-app` `call-recorder` `call-blocker` `contacts` `reminders` `notes` `incallservice` `offline-first` `open-source` `sanstudio`

`#Android #Kotlin #JetpackCompose #Dialer #CallManager #CallBlocker #MVVM #Hilt #Room #SanStudio`

## Developer

**Developed by [SanStudio](https://sanstudio-hub.github.io/in/)**
Portfolio: <https://sanstudio-hub.github.io/in/>

---------------------
Install and Use It




## License

No license has been chosen yet. Until you add a `LICENSE` file, all rights are reserved by SanStudio.
Add an MIT or Apache-2.0 license if you want others to reuse the code.
