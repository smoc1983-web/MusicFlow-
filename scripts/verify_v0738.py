"""MusicFlow 0.7.39: new users get a working-type Telegram invite, no member-only post link."""
from pathlib import Path
from urllib.parse import urlparse

root=Path("app/src/main/java/com/musicflow/app")
updates=(root/"MusicFlowUpdates.kt").read_text(encoding="utf-8")
about=(root/"MusicFlowAboutDialog.kt").read_text(encoding="utf-8")
gradle=Path("app/build.gradle.kts").read_text(encoding="utf-8")
invitation="https://t.me/+IryIQ5fkmOszZWRi"
parsed=urlparse(invitation)

assert parsed.scheme == "https" and parsed.netloc == "t.me"
assert parsed.path.startswith("/+") and len(parsed.path)>3
assert parsed.query == "" and parsed.fragment == ""
assert 'internal const val TELEGRAM_URL = "'+invitation+'"' in updates
assert 'https://t.me/c/3702128532/1' not in updates
assert 'openLink(MusicFlowUpdates.TELEGRAM_URL)' in about
assert 'Вступить в Telegram-канал MusicFlow' in about
assert 'Ссылка откроется для участников канала.' not in about
assert 'versionCode = 739' in gradle and 'versionName = "0.7.39"' in gradle
assert 'applicationId = "com.musicflow.preview"' in gradle
print("PASS: Telegram invitation entry point and 0.7.39 update identity")
