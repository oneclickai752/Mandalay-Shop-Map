# Mandalay Smart Map — Android V0.4 Pilot

**Native Android (Java) app • phone-only APK build with Codemagic • source on GitHub**

## V0.4 changes
- Google web search INSIDE the app (WebView); Facebook and TikTok source filters use Google `site:` searches
- Browse a business page, tap **☆ Save shop**, review name/address/phone/notes/source URL, then save offline in on-device SQLite
- For ordinary business websites with JSON-LD `LocalBusiness` structured data, Save shop attempts to prefill some fields (never guarantees correctness)
- Copy text from a website, then tap **Paste copied info → suggest phone** for optional phone and address suggestions, confirm before saving
- **★ Saved** opens an offline-searchable shop library with stored phone/address/source links
- Blank map is hidden when road data has not been downloaded; saved cards use available screen space
- Prior V0.3: token-based OSM local search, staged Overpass sync, road map, GPS, manual OSM entry

## Important boundaries
- Google, Facebook and TikTok are opened as websites or site-filtered searches. The app does **not** silently scrape/copy all results, nor can it automatically search and unify every shop in Mandalay.
- User-created bookmarks/notes are stored in the phone's private SQLite database after explicit Save. Automated bulk copying from Google's Places service is NOT included.
- Google or social websites may not work normally in embedded WebView; use **↗ Open** to switch to the external browser if necessary.
- **Real AI web aggregation is NOT included.** It requires a separately hosted and authenticated search/AI backend with supported providers, permission/compliance checks, usage quotas and billing.
- Any data copied from Google AI Overview could be incomplete or inaccurate. Check the shop's own site/page before saving.
- OSM Map Sync is downtown pilot coverage only; not guaranteed. Internet needed for web search/sync. Saved records display offline.
- Existing locally saved data may be erased if you uninstall. Android debug APK upgrade can fail when the build's signing key changes. Avoid uninstalling unless you have backed up your data.

## Download a debug APK using an Android phone only
1. Visit **https://codemagic.io**, sign in, open `oneclickai752/Mandalay-Shop-Map`.
2. Press **Start new build**, select latest `main` branch and `android-debug` workflow.
3. Wait for successful build, get **Artifacts → app-debug.apk**, install on Android.
4. If build fails, share the first red step's log, not just the summary.

## Test run
1. Type **ခေတ်ဆန်း mobile** or **jocole mobile** on main screen.
2. Tap **🌐 Web Search** (opens INSIDE app).
3. Search Google or the Facebook/TikTok source filters and open a shop's own page.
4. Tap **☆ Save shop**; review/fill name, address, phone. Optionally Copy relevant page text and use **Paste copied info** inside the save dialog.
5. Save. Tap **★ Saved** and verify the record shows up even with airplane mode ON.
6. Offline Map from OpenStreetMap is a separate data source, not a Google map clone.

© OpenStreetMap contributors / ODbL for OSM data.
