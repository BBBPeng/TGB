TGB Android app
===============

This is a real phone-side app project. It does not need the Windows dashboard
server after installation.

What runs on the phone:
- TGB login cookies are stored in the Android WebView/CookieManager.
- Refresh runs on the phone with HttpURLConnection.
- Main posts, shuo posts, and forum replies are stored on the phone.
- Review notes are stored on the phone.

Build:
1. Install Android Studio.
2. Open this folder:
   C:\Users\D\Documents\Codex\2026-08-04\wo\outputs\tgb_android_app
3. Let Android Studio sync Gradle.
4. Connect Android phone with USB debugging enabled.
5. Click Run, or Build -> Generate APK.

Use:
1. Open the app.
2. Tap "登录淘股吧".
3. Login in the embedded page.
4. Press Android Back to return to the app page.
5. Tap "刷新最新".

Notes:
- iPhone requires a separate iOS project and Apple signing. This folder is for
  Android.
- The app uses no third-party Android libraries.
- If TGB changes its page HTML or API fields, the parser in MainActivity.java
  may need updates.
