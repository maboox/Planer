# Security

Please do not post API keys, signing files, backups or real participant information in issues. Use GitHub's private vulnerability reporting if it is enabled; otherwise contact the repository owner privately before sharing exploit details.

## Controls

- Fixed HTTPS AvalAI endpoint; no caller-supplied URLs or redirects carrying authorization.
- No API or signing credentials in source, APK resources, screenshots or CI logs.
- AES-GCM key storage backed by Android Keystore; local game data remains in the app sandbox.
- No camera, microphone, contacts, location or broad storage permissions.
- Bounded network responses and backup imports; no evaluation of AI output.
- Sensitive UI is covered on backgrounding; screenshot protection is requested through Android `FLAG_SECURE`.
- No automatic telemetry, advertising SDKs or crash uploads.
- CI permissions are read-only except isolated preview/release publishing jobs. PR code never gets release signing secrets.

These controls are not a security certification. Device compromise, deliberate local cheating, malicious backups and semantic AI mistakes remain relevant limits. Dependency and Android lint checks must be reviewed alongside behavior tests before distribution.
