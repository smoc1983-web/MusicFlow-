# MusicFlow — защищённая резервная копия ключа подписи

> **ГДЕ ХРАНЯТСЯ КЛЮЧИ:** [docs/SIGNING_KEYS_REGISTRY_RU.md](docs/SIGNING_KEYS_REGISTRY_RU.md) — проверенные папки Library для основного и TEST ключей, имена backup, SHA-256, параметры OpenSSL и порядок релизов. Эта карта обязательна к чтению перед подписью.

> **Актуальный порядок выпуска основной и TEST версий (обновлён 27.11.2026):** см. [docs/RELEASE_CHANNELS_RU.md](docs/RELEASE_CHANNELS_RU.md). Ниже сохранены первоначальные инструкции восстановления ключа (исторические примеры 0.7.27); для новых версий использовать основной workflow с Secrets либо проверенный приватный JKS → подписанный APK → nonsecret signed-delta → guarded GitHub Release. Не менять исходный сертификат, не загружать пароли или открытый JKS в Git.


Ветку нельзя сделать приватной отдельно: видимость меняется для репозитория целиком.

## Что хранится в Git

Файл `.github/signing/MusicFlow_signing_release.jks.enc.b64` — **зашифрованная**, а не открытая копия исходного `MusicFlow_signing_release.jks`. Это оригинальный ключ, совпадающий по сертификату с официальными версиями 0.7.22–0.7.25.

- Шифрование: OpenSSL AES-256-CBC, PBKDF2, 350 000 итераций, случайная соль.
- SHA-256 исходного JKS: `ef67656ce219d51335bb90ec388fea2ab3632e865016196315ff50f67e7ef72c`.
- Сертификат SHA-256: `c461ee635d5b295ef6c765e1ae533d0079dc6e53cb2632a11a1b445773752410`.
- Пароль для **расшифрования резервной копии** создан отдельно и **не загружен в Git**. Он находится в пользовательском файле `MusicFlow_backup_decryption_passphrase.txt`, полученном отдельно.
- Пароли самого JKS и пароль шифрования — **разные**. Ничего из них нельзя коммитить в GitHub.
- Делайте защищённые резервные копии оригинального ключа и файла с паролем в разных местах.

## Как восстановить ключ вручную

После получения пароля из отдельного файла:

```bash
read -rs -p "Пароль расшифровки: " BACKUP_PASSPHRASE
echo
export BACKUP_PASSPHRASE
base64 -d .github/signing/MusicFlow_signing_release.jks.enc.b64 > MusicFlow_signing_release.jks.enc
openssl enc -d -aes-256-cbc -pbkdf2 -iter 350000 \
  -in MusicFlow_signing_release.jks.enc \
  -out MusicFlow_signing_release.jks \
  -pass env:BACKUP_PASSPHRASE
unset BACKUP_PASSPHRASE
sha256sum MusicFlow_signing_release.jks
```

Сверьте SHA-256 с указанной выше контрольной суммой.

## Для обновления MusicFlow прямо из приложения

Откройте **Settings → Secrets and variables → Actions** в репозитории и добавьте четыре секрета:

1. `MUSICFLOW_SIGNING_BACKUP_PASSPHRASE` — значение отдельного файла с паролем резервной копии
2. `MUSICFLOW_SIGNING_STORE_PASSWORD` — исходный пароль JKS, из ранее предоставленных настроек
3. `MUSICFLOW_SIGNING_KEY_ALIAS` — `musicflow`
4. `MUSICFLOW_SIGNING_KEY_PASSWORD` — исходный пароль ключа

Не вставляйте пароли в issue, commit, PR, README, workflow или чат.

Затем запустите GitHub Actions → **Publish MusicFlow 0.7.27 in-app update** → **Run workflow** на ветке `feature/musicflow-v0.7.27-reliable-updates`.

Workflow восстановит ключ только на временном CI runner, проверит сертификат и соответствие официальному APK, соберёт release APK и опубликует GitHub Release. После публикации официальный MusicFlow должен показать обновление в интерфейсе. Для установленного `com.musicflow.preview.vktest` обновление таким способом невозможно: это отдельное приложение.
