# Project Rules & Persistent Guidelines

## Brand Logo & App Icon Protection Rule
- The DEX Logistics company logo (`dex_brand_logo.jpg`) and app icon (`dex_app_icon.jpg` / `ic_launcher`) in `app/src/main/res/drawable/` are permanent, official brand assets.
- **NEVER** delete, overwrite, replace, or alter `dex_brand_logo.jpg` or `dex_app_icon.jpg`.
- **NEVER** remove the logo references in `LoginScreen.kt`, `HomeScreens.kt`, or `AndroidManifest.xml` during future file edits or refactoring.

## User Management Rules
- Admins are permitted to delete non-admin user accounts (`deleteUserAccount`).
- Account deletion must clean up data across local Room DB, Supabase `profiles` & `user_roles`, and Firebase Firestore `users` collection.
- Primary administrator (`ADM-001`) and currently logged-in account are locked against deletion.

## Version & Updates
- Updates use Firebase Remote Config (`latest_version_code`, `latest_version_name`, `release_notes`, `download_url`) with Supabase REST API fallback for OTA updates.
