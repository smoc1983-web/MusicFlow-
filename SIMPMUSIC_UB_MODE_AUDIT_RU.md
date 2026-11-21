# MusicFlow 0.7.31 — исследование SimpMusic и применение ub-mode

## Изученные оригиналы
- https://github.com/maxrave-dev/SimpMusic — Kotlin Compose Multiplatform, Media3, GPL-3.0, недокументированные YouTube Music API.
- https://github.com/unclebob/ub-mode — MIT, `SKILL.md` (First Make it Work → Then Make it Right → Practice).

## Почему не переносился SimpMusic целиком
Различные архитектура, лицензия и музыкальный backend; монолитное объединение нарушило бы принцип минимальных проверяемых изменений. Код GPL-3.0 не копировался; YouTube Music scraper не был интегрирован и нельзя считать его документированным официальным API.

## Функции первой итерации (реализованы независимо)
1. SleepTimerPolicy: чистые правила времени; MusicFlowSleepTimerStore: одна дата завершения; PlaybackService: единственный исполнитель действия pause с сохранением очереди.
2. LyricsTiming: чистый расчёт позиции текста (бинарный поиск, ±5000 мс); интерфейс коррекции ±250 мс с сохранением в приложении.

## ub-mode — журнал проверок
- **Red TDD:** коммит тестов `2146d99769f54df08bd8f67dc1f33af212404c66`, ошибочный прогон https://github.com/smoc1983-web/MusicFlow-/actions/runs/44905189587 — отсутствовали `SleepTimerPolicy` и `LyricsTiming`, ожидаемая причина.
- **Fix:** реализованы минимальные функции и пользовательское подключение к Media3/Compose в отдельных последовательных коммитах.
- **Behavior tests:** `app/src/test/java/com/musicflow/app/SleepTimerPolicyTest.kt`, `LyricsTimingTest.kt`. Выполняются настоящим `:app:testDebugUnitTest`, а не только Python поиском подстрок.
- **Existing regressions:** Python/CI прежних версий (источники, ТВ, обновления и подпись).
- **Unsupported Kotlin gates:** оригинальные `unclebob/crapper`, `dryer`, `mutator` перечисляют Clojure, Java, Go, TypeScript, Rust и Python, но не Kotlin. Нельзя указывать CRAP/dryer/mutation оценки без подходящего Kotlin анализатора.
- **Policy:** фиксировать результаты CI и проблемы перед релизом; публиковать только с разрешения пользователя после проверки подписи APK.

## Нерешённое
- Поведение таймера, синхронизации текстов и фонового воспроизведения ещё нужно испытать на физическом Android-телефоне.
- Текущая итерация не вводит кроссфейд, скачивание музыки и новый музыкальный провайдер.
