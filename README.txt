MGLegacy alpha 0.1 - FCL renderer plugin (Kotlin + Compose + Miuix)

WHAT'S REAL VS GUESSED (read this first)
-----------------------------------------
Every setting key in this app's "Renderer" and "Experimental" tabs
(customGLVersion, fsr1Setting, maxGlslCacheSize, enableAngle,
enableExtComputeShader, enableExtDirectStateAccess, enableExtTimerQuery,
ignoreError) was found as a literal string INSIDE libmobileglues.so's own
debug-log output ("[MobileGlues] Setting: <key> = <value>"), so the KEYS
are confirmed real, not guessed. What is NOT independently confirmed:
  - the exact valid text for customGLVersion (leave blank for default)
  - the exact integer meaning of each FSR level (0/1/2/3 mapping is our
    best inference from the option order MobileGlues logs, not source-verified)
  - the unit for maxGlslCacheSize

HOW IT ACTUALLY WORKS
-----------------------------------------
FCL never opens this app. FCL only reads the <meta-data> in
AndroidManifest.xml once, when it scans installed apps, to find the
renderer name/libraries/env vars. This app's UI writes a real MobileGlues
config file (config.json) to a folder that the manifest's MG_DIR_PATH env
var points MobileGlues at. So the correct order every time you change a
setting is:
  1. Open this app
  2. Change settings
  3. Tap "Save settings"
  4. THEN launch Minecraft in FCL with the "MgLegacy alpha 0.1" renderer

IMPORTANT CAVEAT FOUND IN THE BINARY: it contains the string "Unsupported
launcher detected, force using default config." MobileGlues appears to
check which launcher is invoking it and may ignore config.json entirely
for launchers it doesn't recognize. We don't know FCL's status here - if
your settings don't seem to take effect, this check may be why.

BUILD STEPS
-----------------------------------------
1. Your .so files already live in: app/src/main/jniLibs/arm64-v8a/
2. Push everything to GitHub (keep the repo PRIVATE).
3. Actions tab -> wait for the green tick -> download artifact "MGLegacy-apk".
4. Install the APK, open it once, set your options, tap Save.
5. In FCL: instance settings -> Renderer -> "MgLegacy alpha 0.1".

If it still crashes on 1.16.5, the "ignoreError" toggle (on by default in
this app) is the most evidence-backed thing to test next - it's a real
MobileGlues setting, not a borrowed/guessed env var like our earlier
LIBGL_NOERROR attempt (which doesn't exist in either library and did
nothing).
