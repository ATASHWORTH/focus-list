# Focus List

A checklist with a built-in pomodoro timer.

- `web/index.html` is the whole app. Netlify serves it as the website (see `netlify.toml`).
- `app/` is a small Android wrapper around the same page. It hands each period's end time to Android's
  alarm system, so the alert rings even when the phone is locked or the app is closed.
- Every push to `main` builds the APK on GitHub Actions and posts it under **Releases → latest**.

## Editing

`tools/index.template.html` is the source for the page; `web/index.html` is the same file with the two sounds
embedded. `tools/synth.py` generates the rooster and cluck sounds (`app/src/main/res/raw/*.mp3`).
