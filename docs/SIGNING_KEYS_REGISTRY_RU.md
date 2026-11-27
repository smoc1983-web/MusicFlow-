# MusicFlow — постоянный реестр мест хранения ключей (OFFICIAL + TEST)

**Запись подтверждена 27.11.2026. Этот файл — первая точка входа для КАЖДОЙ следующей сборки и подписи.** Сначала прочитать этот файл, `AGENTS.md`, `docs/RELEASE_CHANNELS_RU.md`, последние записи `DEVELOPMENT_JOURNAL.md`. Ничего не искать по старым чатам до проверки указанных ниже мест.

**Это только карта мест хранения и проверочные отпечатки.** Пароли, приватный JKS/P12 и незашифрованные ключи никогда не помещать в GitHub, журналы, issue, чат или CI-артефакты.

## 1. OFFICIAL — основное приложение

- **Android package:** `com.musicflow.preview`.
- **Оригинальный сертификат SHA-256:** `c461ee635d5b295ef6c765e1ae533d0079dc6e53cb2632a11a1b445773752410`.
- **SHA-256 исходного JKS:** `ef67656ce219d51335bb90ec388fea2ab3632e865016196315ff50f67e7ef72c`.
- **Alias:** `musicflow`.
- **Шифрованный backup исходного JKS в самом GitHub:** [`.github/signing/MusicFlow_signing_release.jks.enc.b64`](../.github/signing/MusicFlow_signing_release.jks.enc.b64), ветка `main`. **Только шифрованный** backup; исходный JKS туда никогда не коммитить.
- **Приватная Library аккаунта ChatGPT:** папка **`/MusicFlow_Private_OfficialSigning/`**, подтверждённое содержимое:
  - `MusicFlow_backup_decryption_passphrase.txt` — отдельный пароль от backup.
  - `MusicFlow_GitHub_Actions_Secrets.txt` — ранее предоставленные владельцем параметры основного JKS (конфиденциальный источник).
- **Алгоритм резервной копии:** OpenSSL AES-256-CBC, PBKDF2, **350 000** итераций, Salted__, исходный backup в base64. Пароль расшифрования **отличен от JKS password**. Для восстановления использовать защищённую временную среду, `openssl enc -d -aes-256-cbc -pbkdf2 -iter 350000`, пароль передавать только через переменную окружения, сверить SHA исходного JKS и сертификат.
- **GitHub Actions Secrets (если настроены):** `MUSICFLOW_SIGNING_BACKUP_PASSPHRASE`, `MUSICFLOW_SIGNING_STORE_PASSWORD`, `MUSICFLOW_SIGNING_KEY_ALIAS`, `MUSICFLOW_SIGNING_KEY_PASSWORD` — можно просмотреть *факт наличия*, не значения. **На выпусках 0.7.51–0.7.52 эти Secrets отсутствовали**, поэтому применяли приватную подпись и проверяемый nonsecret signed-byte-delta.
- **Рабочий шаблон выпуска:** `.github/workflows/publish-v0752-original-signed.yml`; подпись только исходным official ключом, source CI success + SHA-256 + original certificate + Android v2/v3 + `gh release create vX.Y.Z`, файл `MusicFlow_X.Y.Z.apk`, **не** prerelease.

## 2. TEST — постоянный тестовый канал

- **Android package:** `com.musicflow.preview.dev` (начиная с базовой TEST 0.7.54). Ранние пакеты вроде `com.musicflow.preview.radioarttest50` несовместимы по package.
- **Постоянный TEST сертификат SHA-256:** `5b021afbd43d8c4fc1de501bd5e000c8d0286da573a700121ace77339f199835`, **не совпадает с основным**.
- **Alias TEST PKCS12:** `musicflow-test`.
- **Контрольный SHA-256 расшифрованного TEST P12:** `7b4513ce918e576dc2aeadc00372725a4db6513f8436db73180430c5303d0bcb`.
- **Приватная Library аккаунта ChatGPT:** папка **`/MusicFlow_Private_TestSigning/`**, подтверждённое постоянное содержимое:
  - `MusicFlow_TEST_stable_v1.p12.enc.b64` — **зашифрованная** копия оригинального постоянного TEST PKCS12.
  - `MusicFlow_TEST_signing_secrets.json` — **закрытый** файл с полями `backup_passphrase`, `store_password`, `key_password`, `alias`. **Никогда не публиковать содержимое**. Временные `_TEMP_*delta*` не относятся к ключам и не нужны для восстановления.
- **Подтверждённый алгоритм расшифрования TEST:** OpenSSL `enc -d -aes-256-cbc -pbkdf2 -iter 350000`; b64 декодировать в .enc, пароль `backup_passphrase` из личного JSON передать через переменную окружения или `-pass env:`; восстановленный P12 сверить по SHA-256 выше. Для проверки сертификата использовать Android `apksigner verify --verbose --print-certs` на готовом APK и ожидать SHA-256 `5b021afb…99835`.
- **Проверенные базовые версии:** [TEST 0.7.54](https://github.com/smoc1983-web/MusicFlow-/releases/tag/v0.7.54-test), [TEST 0.7.55](https://github.com/smoc1983-web/MusicFlow-/releases/tag/v0.7.55-test). **Для каждого следующего TEST APK использовать этот же P12**, а не ephemeral Android debug key CI. В `main` TEST ключ не переносить.
- **Шаблон релиза:** `.github/workflows/publish-v0755-stable-test.yml` в [ветке 0.7.55](https://github.com/smoc1983-web/MusicFlow-/tree/feature/musicflow-media3-download-library-v0755). Для 0.7.56 — `.github/workflows/publish-v0756-stable-test.yml` и `.github/release-assets/v0756-test/` в соответствующей TEST-ветке. Публикация: `vX.Y.Z-test` (пререлиз), APK `MusicFlow-TEST_X.Y.Z.apk`.

## 3. Порядок восстановления/подписи: НЕ повторять поиск ключей

1. Взять официальный encrypted JKS из GitHub + параметры из **OfficialSigning** Library **ИЛИ** TEST encrypted P12 + параметры из **TestSigning** Library. Никогда не смешивать.
2. Временно материализовать приватные файлы в безопасную рабочую директорию, расшифровать OpenSSL AES-256-CBC PBKDF2 **350 000**, сверить SHA исходного хранилища и сертификат. Не вставлять пароль в командную строку в открытом виде, не выводить его в лог.
3. Взять **точный APK** из последнего прошедшего GitHub Actions run (сначала JUnit + Android compile). Подписывать `apksigner` (Android v2/v3), сверить пакет, `versionCode`, сертификат и SHA-256 APK. Не пытаться ставить неподписанный или иначе подписанный APK поверх существующей версии.
4. Если в Actions нет секретов: локально получить подписанный APK, сделать неперсональный `zstd --patch-from=unsigned.apk signed.apk` и SHA manifest, опубликовать **только delta и SHA** в GitHub, запустить guarded workflow, сверить воспроизведённый APK байт-в-байт. Никаких приватных ключей в GitHub.
5. Убедиться, что GitHub Actions подписанный publish run завершился `success`, что Release содержит корректный APK и digest, и только тогда говорить «выпущено». Установка на телефоне — отдельная ручная проверка.
6. **После работы:** удалить временные незашифрованные JKS/P12, временные файлы с паролями и delta из общего каталога. **Не удалять приватные постоянные backup** из двух Library-папок. Зафиксировать в `DEVELOPMENT_JOURNAL.md` версию, ветку, CI run, sha, сертификат, GitHub Release, проблемы и результат проверки.

> Зашифрованные файлы могут быть недоступны в другом окружении/аккаунте, пока не подключена та же Library. Сначала проверять наличие конкретного файла; если Library недоступна, **не создавать новый signing key**, а сообщить о невозможности доступа к прежнему.
