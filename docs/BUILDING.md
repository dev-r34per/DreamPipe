# Building DreamPipe

Use **JDK 21**, Android SDK **37**, build-tools **37.0.0**, and NDK **21.0.6113669**.
Java source compatibility remains 17. The wrapper is Gradle 9.6.1 and AGP is 9.2.1;
these are the existing pinned NewTube toolchain versions.

If cloning a Git repository, initialise its pinned modules:

```bash
git submodule update --init
```

A complete source ZIP already includes the two top-level module checkouts and can be built
without Git metadata. MediaServiceCore's nested SharedModules checkout is unused: Gradle
selects the top-level sibling SharedModules explicitly.

Set `sdk.dir=/absolute/path/to/android-sdk` in your untracked `local.properties`, or set
`ANDROID_HOME`. Install missing components with your SDK manager:

```bash
sdkmanager 'platforms;android-37' 'build-tools;37.0.0' 'ndk;21.0.6113669'
```

## Checks and debug APK

```bash
./gradlew --no-daemon :common:testDebugUnitTest :smarttubetv:testStmobileDebugUnitTest :smarttubetv:lintStmobileDebug :smarttubetv:assembleStmobileDebug
```

Output: `smarttubetv/build/outputs/renamed_apks/stmobileDebug/DreamPipe_1.0.4_<abi>.apk`.
Use `arm64-v8a` on a Titan 2 Elite, or `universal` if unsure.

```bash
adb -s DEVICE_SERIAL install -r smarttubetv/build/outputs/renamed_apks/stmobileDebug/DreamPipe_1.0.4_arm64-v8a.apk
```

Without `keystore.properties`, debug uses the generated debug key and `app.dreampipe.debug`.
With your release key configured, debug uses that key and the unsuffixed ID, as upstream did.
DreamPipe installs separately from NewTube and does not migrate or remove its data.

For an x86_64 emulator add `-PemulatorAbi`. Keep minification off: upstream's reflection over
Media3 internals depends on that setting.

## Signed local release

Create your own long-lived Android signing key on your machine. Keep it and its passwords
outside version control. The existing signing configuration reads this untracked file:

```properties
# keystore.properties in the project root
storeFile=/absolute/path/to/your/dreampipe.jks
storePassword=YOUR_PASSWORD
keyAlias=YOUR_ALIAS
keyPassword=YOUR_KEY_PASSWORD
```

```bash
./gradlew --no-daemon :smarttubetv:assembleStmobileRelease -PdreampipeRepositoryUrl=https://github.com/YOUR_NAME/DreamPipe
```

Release output is under `smarttubetv/build/outputs/renamed_apks/stmobileRelease/`.
The same signing key is required for future updates.

## Portable host checks

```bash
bash tools/test-dreampipe-host.sh
```

This uses the actual model, JSON/M3U codec, navigation and row policy, local session queue,
and serial playlist/history store code. The script fetches a pinned JSON-Java test dependency
and uses small Android I/O fixtures that are never packaged in the APK. To run offline with
an existing JSON-Java source checkout, pass its directory as the first argument. These checks
do not replace Android/Robolectric or on-device lifecycle validation.
