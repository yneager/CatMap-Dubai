# Crash reports (Android, Firebase Crashlytics)

Added 27 Oct 2026 for the public release. Free on the Spark plan.

## What is sent, and when

* Only **release** builds send reports (`BuildConfig.CRASH_REPORTS`). Debug and staging builds, tests and the local
  emulators never do.
* The manifest turns collection **off** (`firebase_crashlytics_collection_enabled=false`). `CatMapApp` then turns it on
  when the person's **Settings → Privacy → Send crash reports** switch is on (the default) and off when they turn it
  off (`util/CrashReports.kt`). A crash during the very first start, before the setting is applied, isn't sent.
* A report holds the stack trace, phone model, Android and app version, and a random installation ID. Firebase
  Sessions (bundled with Crashlytics) also notes app starts, to work out crash-free rates. It sends only while
  collection is on. No user ID is set: posts, messages, photos and places are never attached.
* Caught errors that reach `AppErrors.report` are recorded as non-fatal, except offline and slow-network errors
  (`CrashReports.expected`) and errors right after sign-out.
* The privacy policy (`web/public/privacy.html`) describes this. Keep the two in step.

The web app has no crash reporting: Crashlytics has no web SDK. Adding one would mean a third-party service, or
writing reports into Firestore, which costs writes, needs rules and invites spam. That waits until there's a need.

## Reading a crash

Firebase console → **Crashlytics** (project `catmap-dubai`). The first report makes the dashboard appear.

Release builds are shrunk by R8, so their stack traces have short names. The automatic mapping upload needs Google's
google-services Gradle plugin, which this build replaces (`firebaseResources` in `app/build.gradle.kts`), so it is
off. Instead, `release.yml` keeps `mapping.txt` as the `r8-mapping` artifact of each release run for 90 days. The
source is public, so the file holds nothing secret. To make a release's crashes readable:

1. GitHub → **Actions** → the `Release APK` run for that tag → download **r8-mapping**, and unzip it.
2. In the `cat` folder: `npx -y firebase-tools@15.34.0 crashlytics:mappingfile:upload --app=<Android app ID> mapping.txt`.
   The Android app ID is in Firebase console → Project settings → Your apps (it starts with `1:`).

Crashlytics keeps reports for 90 days.
