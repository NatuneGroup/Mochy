# Test signing key

`mochy-test.p12` is a published, non-secret release key. Gradle uses it only when
`KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` are all absent,
which is the case for this fork's GitHub Actions builds.

The file exists so consecutive CI builds share one signature. Android refuses to
install an update signed by a different key, and a fresh GitHub Actions runner has
no reusable `~/.android/debug.keystore`; falling back to the debug config produced a
new signature on every run.

- Store and key password: `mochytest`
- Key alias: `mochy-test`
- Certificate: `CN=Mochy Test Build, OU=Mochy Test Build, O=NatuneGroup, C=US`
- Certificate SHA-256: `0F:32:3E:19:D9:B2:D8:37:B9:7E:E7:D4:D5:F8:3B:7A:7B:9F:F0:38:8A:F6:9D:40:26:6C:5E:D4:74:06:FE:49`
- Validity: 100 years from 2026-11-24

This key is not a secret and must not be used for a store release. When the
`KEYSTORE_*` secrets are configured, the `release` signing config takes precedence.
