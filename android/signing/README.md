# Android release signing

The production key is stored locally as `yesplaymusic-release.p12` and is
intentionally excluded from Git.

Gradle reads these Windows user environment variables:

- `YPM_RELEASE_STORE_PASSWORD`
- `YPM_RELEASE_KEY_PASSWORD`
- `YPM_RELEASE_KEY_ALIAS` (defaults to `yesplaymusic-release`)

Back up the `.p12` key and the three values together. Every future update for
the same Android application ID must be signed with this key.
