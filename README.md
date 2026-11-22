# Michelle

Take a photo of your meal; Claude says what's on the plate and how many calories (and protein, carbs, fat)
it is. Fix what it got wrong, save, and the meal is kept on the phone and written to Health Connect, so
your other health apps see it. The home screen shows today's meals and the day's total.

For one person on a Pixel 10 (Android 16), installed over USB. Uses your own Anthropic API key.

## Build and run

Needs the Android SDK in `~/Android/Sdk` (platform 36, build-tools 36.1.0) and JDK 21. A phone with USB debugging on, then:

```
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk   # -r keeps your meals
adb shell am start -n dev.michelle/.MainActivity
./gradlew testDebugUnitTest                                 # the unit tests
```

Never uninstall it or clear its data on a phone you use: that deletes your meals and photos.

## The API key

Michelle needs an Anthropic API key (console.anthropic.com → API Keys). Put it in
`.secrets/anthropic_api_key` (not in git; the key alone or `ANTHROPIC_API_KEY=sk-ant-...`), unlock the
phone and run:

```
tools/set-key.sh               # or: tools/set-key.sh --clipboard, or without the file it asks
```

It opens Michelle's settings with the key field focused, types the key and saves it. The key is kept
encrypted with a key from the Android Keystore; it's never logged. It can also be typed in settings.

## Using it

- **Photo** (bottom right): the Pixel camera opens; take the picture and confirm. Claude needs a few
  seconds (about 6 s with Opus 5.5).
- Check the items: change any food, portion, kcal or grams, remove items (×), add one. The title, the
  meal type (guessed from the time) and the time can be changed too. **Save**, or ✕ to discard.
- No food in the photo, or no network: retake, retry, type it in by hand or discard.
- Tap a meal on Today to change or delete it; Health Connect follows.
- Settings (gear): the key, the model (Opus 5.5 by default, about 2–4 cents a meal; Sonnet 5.5; Haiku
  5.5, a fraction of a cent), Health Connect permission and "sync now".

Health Connect: Michelle asks once, after the first save, for permission to write nutrition (nothing
else, it never reads). A meal that couldn't be written shows a small cloud icon and is written the next
time Michelle opens, or with "sync now".

## Release build

`./gradlew assembleRelease` makes `app/build/outputs/apk/release/`, signed with the key in the file
named by `MICHELLE_KEYSTORE` (with `MICHELLE_KEYSTORE_PASSWORD`, `MICHELLE_KEY_ALIAS`) in
`~/.gradle/gradle.properties`, outside the repo; without them it comes out unsigned. A release build
can't replace a debug build (different signatures) without uninstalling, which deletes the meals, so
stay on one kind.

## Checking an analysis without the camera (debug builds)

```
adb push food.jpg /data/local/tmp/ && adb shell chmod 644 /data/local/tmp/food.jpg
adb shell run-as dev.michelle sh -c "'mkdir -p cache/capture && cp /data/local/tmp/food.jpg cache/capture/t.jpg'"
adb shell am start -n dev.michelle/.MainActivity --es photo /data/user/0/dev.michelle/cache/capture/t.jpg
adb logcat -s michelle     # timing and Claude's JSON
```

Delete the test meal afterwards (open it → trash).

## Layout

```
tools/set-key.sh            the API key onto the phone
app/src/main/java/dev/michelle/
  App.kt                    the app's parts; syncs Health Connect on start
  MainActivity.kt           the screens (Today, Meal, Settings) and the camera
  Meals.kt                  save/delete: the database first, Health Connect after
  Secrets.kt, Settings.kt   the API key (Keystore-encrypted), the model
  ai/Analyzer.kt            Claude: the photo in, JSON out (structured output), errors
  ai/Prompt.kt, Analysis.kt what Claude is asked, the JSON schema, parsing
  data/                     Room: meals, items, Health Connect deletes still to do; meal types; totals
  photo/Photos.kt           camera file, upright, 1600 px JPEG in the app's files
  health/HealthWriter.kt    Health Connect: a meal = one NutritionRecord ("meal-<id>")
  ui/                       Compose screens, theme (greens), photos
```
