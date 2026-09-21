# DrawingJoy — Private Drawing App for Android

A simple, private, offline whiteboard-style drawing app. No login, no accounts,
no ads, no tracking. Open → Draw → Save → Record → Share.

## What's inside

- A full Android Studio project (Kotlin), source in `app/src/main/java/com/mom/privatedrawing/`
- A left-side color palette with a custom color wheel picker and a saved
  "My Colors" section (stored locally on the device)
- Pen, Pencil, Marker, Highlighter, Eraser, Fill, Text, Line, Rectangle,
  Circle, Triangle, Star, and Image import tools
- Undo / Redo / Clear (with confirmation)
- Save as PNG (canvas only, no toolbar) and Share via any installed app
- Screen recording to MP4 with a visible recording indicator and timer, plus
  Preview / Save / Share / Delete after stopping
- A Full-Screen mode that hides the side panel and toolbar
- `.github/workflows/build-apk.yml` — builds a debug APK automatically in
  GitHub's free cloud runners, no paid service, nothing published to the Play
  Store

**Important honesty note on recording:** Android's screen-recording API
(MediaProjection) records the *whole screen*, not just the canvas view inside
the app. Using Full-Screen mode before recording gets you the cleanest result,
since it hides the toolbar and palette — but a system status bar or another
app's notification popping up mid-recording could still appear. There is no
way around this without much heavier custom rendering, so it's called out
here rather than left as a silent limitation.

---

## Step 1 — Put the project on GitHub

1. Go to https://github.com and log in (create a free account if you don't
   have one).
2. Click the **+** in the top-right corner → **New repository**.
3. Name it something like `my-drawing-board`, keep it **Private** (recommended,
   since this is for personal/family use), and click **Create repository**.
4. On the new repo's page, click **uploading an existing file** (or, if you're
   comfortable with git on your computer, use the command line instead — see
   the box below).
5. Drag the **entire contents** of this project folder in (keep the folder
   structure — `app/`, `.github/`, `build.gradle`, etc.) and commit.

**Command-line alternative**, run from inside this project folder:
```bash
git init
git add .
git commit -m "Initial commit: private drawing app"
git branch -M main
git remote add origin https://github.com/YOUR-USERNAME/my-drawing-board.git
git push -u origin main
```

## Step 2 — Run the GitHub Actions build

The workflow is already set to run automatically as soon as you push to the
`main` branch, so simply completing Step 1 will kick it off. To trigger it
manually instead (or run it again):

1. In your repository on GitHub, click the **Actions** tab.
2. Click **Build APK** in the left sidebar.
3. Click **Run workflow** → **Run workflow**.
4. Wait a few minutes — you'll see a spinning yellow icon that turns into a
   green check mark when the build succeeds.

## Step 3 — Download the APK

1. In the **Actions** tab, click on the finished workflow run (the one with
   the green check mark).
2. Scroll down to the **Artifacts** section at the bottom of that page.
3. Click **private-drawing-app-debug-apk** to download it as a `.zip` file.
4. Unzip it — inside you'll find `app-debug.apk`.

## Step 4 — Install it on your mother's Android phone

1. Get `app-debug.apk` onto her phone (email it to yourself and open the
   attachment on her phone, use a USB cable, share it via a messaging app, or
   upload it to Google Drive and download it on the phone — any method
   works).
2. On her phone, tap the APK file to install it.
3. Android will likely show a warning like "install blocked" or "unknown
   source" the first time — this is normal for any app installed outside the
   Play Store. Tap **Settings** in that prompt, allow installs from that
   source (e.g. Files, Chrome, or Gmail — whichever app you used), then go
   back and tap install again.
4. Once installed, open **DrawingJoy** from the home screen. No sign-up,
   no setup — it goes straight to the drawing screen.

That's it: Open → Draw → Save → Record → Download → Share.

---

## Notes for future changes

- The app is unsigned (uses the standard Android debug key), which is normal
  and fine for private, non-Play-Store installs — it just means each fresh
  install replaces the last one cleanly.
- All custom colors are stored in `SharedPreferences` on-device only.
- All drawings save to `Pictures/DrawingJoy` in the phone's normal gallery.
- All recordings save to the app's private storage and are only accessible
  through the app's own Share/Delete options, or via a file manager.
- Nothing in this app calls the internet, and no analytics or ad SDKs are
  included anywhere in the project.
