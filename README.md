# gotosleep-android

## Build example


```bash
chmod +x ./gradlew
export JAVA_HOME=/home/$USER/jdks/jdk-17.0.18+8
./gradlew assembleDebug --no-daemon
```

`local.properties` (not in git) points the SDK at:

```properties
sdk.dir=/home/$USER/android-sdk
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

If `./gradlew` fails with JDK/tool errors, use a **full JDK 17** (not a JRE-only install) and ensure `local.properties` exists with a valid `sdk.dir`.
