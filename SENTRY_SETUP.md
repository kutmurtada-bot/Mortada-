# Sentry + QA setup

The Android app now contains Sentry runtime error monitoring and the GitHub QA gate.

## One required manual step

The Sentry DSN is intentionally **not committed to the repository**. Add it as a GitHub Actions repository secret named:

`SENTRY_DSN`

The workflows read it through `${{ secrets.SENTRY_DSN }}` and pass it to Gradle as an environment variable. If the secret is absent, the app still builds and runs, but Sentry stays disabled and logs a warning.

Create the Sentry Android project first, then copy its DSN into the GitHub secret. Do not paste the DSN into source code, README files, or commits.

## What is monitored

- Uncaught Android exceptions through the Sentry Android SDK.
- File import/export/share/open failures.
- WebView JavaScript console errors at ERROR level.
- App release/version and `prod` environment metadata.
- Sentry default PII collection is disabled.
- Screenshot attachment is disabled.

## QA gate

`.github/workflows/qa.yml` runs on pushes to `main`, pull requests, and manual dispatch.

It performs:

1. Synthetic data integrity tests at 1,245, 5,000 and 10,000 records.
2. Search regression checks.
3. Backup/restore round-trip checks using synthetic data.
4. Distribution-state accounting checks.
5. Android lint.
6. Android unit-test task.
7. Debug APK build and existence check.
8. APK and lint-report artifacts.

The QA summary only passes when the required jobs pass.

## Important limitation

CI QA cannot prove that an Android app will never have an error. It creates a release gate that catches repeatable regressions before testing/release, while Sentry catches runtime failures that occur on real devices after deployment.
