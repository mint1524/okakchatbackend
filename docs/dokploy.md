# OKAK Chat Backend — Деплой через Dokploy

Dokploy использует Traefik как reverse proxy. SSL-сертификаты выдаются автоматически через Let's Encrypt. В этом гайде `api.okak.club` — пример домена, замени на свой.

---

## Требования

- Dokploy установлен и доступен (обычно на порту 3000)
- GitHub аккаунт подключён к Dokploy (Settings → Git Providers)
- Домен делегирован на IP сервера, `A`-запись настроена
- OKAK Mail v2 уже запущен и доступен по внутреннему URL или домену

---

## Шаг 1 — Подготовить секреты

Сгенерируй все секреты **до** создания проекта. Понадобятся:

```bash
# JWT_SECRET — 32+ случайных байта
openssl rand -hex 32

# POSTGRES_PASSWORD — любой надёжный пароль
openssl rand -hex 16

# AI_API_KEY_ENCRYPTION_SECRET — ровно 32 символа (AES-256)
openssl rand -hex 16   # даёт 32 hex-символа
```

---

## Шаг 2 — Создать Stack в Dokploy

1. Открой Dokploy → **Projects** → кнопка **Create Project** → дай имя `okakchat`
2. Внутри проекта нажми **Create Service** → выбери **Stack**
3. В поле **Source** выбери **GitHub** → укажи репозиторий `mint1524/okakchatbackend`
4. **Branch**: `main`
5. **Compose Path**: `docker-compose.yml`
6. Нажми **Save**

---

## Шаг 3 — Задать переменные окружения

В настройках Stack перейди на вкладку **Environment** и вставь:

```env
# Domain — твой домен для API
DOMAIN=api.okak.club

# Database
POSTGRES_DB=okakchat
POSTGRES_USER=okak
POSTGRES_PASSWORD=<сгенерированный пароль>

# JWT
JWT_SECRET=<64-символьный hex из openssl rand -hex 32>
JWT_ACCESS_TTL_MINUTES=15
JWT_REFRESH_TTL_DAYS=30

# OKAK Mail
MAIL_SERVICE_BASE_URL=https://mail-api.okak.club
MAIL_SERVICE_TOKEN=<токен из OKAK Mail>
MAIL_SITE_URL=https://okak.club

# AI Proxy
AI_API_KEY_ENCRYPTION_SECRET=<32-символьный hex>
```

> `MAIL_SERVICE_BASE_URL` — это адрес твоего OKAK Mail v2. Если он в той же Docker-сети Dokploy, можно использовать внутреннее имя контейнера (`http://okak-mail-api:3035`). Если на отдельном домене — используй HTTPS-адрес.

---

## Шаг 4 — Первый деплой

1. В настройках Stack нажми **Deploy**
2. Dokploy клонирует репозиторий, соберёт все три образа (Gradle multi-stage build, ~5–10 минут при первой сборке) и запустит Stack
3. Следи за логами в **Logs** → прогресс Gradle-сборки будет виден

> Если билд упал — проверь в логах конкретный сервис. Типичная причина: Gradle не скачал зависимости при первом запуске. Нажми **Redeploy**.

---

## Шаг 5 — Привязать домен

1. В Stacks найди сервис **nginx** → вкладка **Domains**
2. Нажми **Add Domain**
3. Введи `api.okak.club` (или свой домен)
4. **HTTPS**: включи, выбери **Let's Encrypt**
5. **Port**: `80` (внутренний порт nginx-контейнера)
6. Сохрани — Dokploy сам выпустит сертификат и настроит Traefik

> DNS должен уже резолвиться на IP сервера. Let's Encrypt проверяет домен по HTTP-challenge.

---

## Шаг 6 — Проверить деплой

```bash
# Health check
curl https://api.okak.club/health

# Ожидаемый ответ:
{"ok":true}
```

Если всё ОК — auth-service отвечает через Nginx.

---

## Шаг 7 — Bootstrap первого администратора

После первого запуска нужно вручную дать кому-то роль superadmin. Сначала зарегистрируй аккаунт через Flutter-приложение или напрямую:

```bash
curl -X POST https://api.okak.club/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"your@email.com","password":"yourpassword","displayName":"Admin"}'
```

Тебе придёт письмо с кодом. Подтверди:

```bash
curl -X POST https://api.okak.club/api/auth/verify \
  -H "Content-Type: application/json" \
  -d '{"userId":"<userId из ответа register>","code":"<6-значный код>"}'
```

Сохрани `accessToken` из ответа. Теперь найди UUID пользователя в БД и дай роль:

**Через Dokploy Terminal** (Stack → сервис postgres → Terminal):
```sql
-- Подключиться к БД
psql -U okak -d okakchat

-- Узнать UUID пользователя
SELECT id, email FROM users WHERE email = 'your@email.com';

-- Дать роль superadmin
INSERT INTO admin_roles (user_id, role, granted_at)
VALUES ('<uuid>', 'superadmin', NOW());

\q
```

После этого войди заново (получи новый JWT, в котором `admin: true`).

---

## Шаг 8 — Добавить AI-провайдера

API-ключи хранятся зашифрованными. Нужно зашифровать ключ и вставить его в БД.

Через **Dokploy Terminal** (сервис `ai-proxy`):

```bash
# Запустить Kotlin REPL или использовать готовый скрипт
# Проще всего — добавить временный admin-endpoint для шифрования ключа

# Вариант: сделать напрямую через psql с уже зашифрованным значением
# Или использовать такой однострочник на Java:
```

Самый простой способ — через psql, предварительно зашифровав ключ локально.

Склонируй репо локально и запусти Kotlin-скрипт для шифрования:

```bash
cd okakchatbackend

# Создай временный скрипт encrypt.kts
cat > /tmp/encrypt.kts << 'EOF'
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

val secret = args[0]  // AI_API_KEY_ENCRYPTION_SECRET
val plaintext = args[1]  // твой API ключ

val key = SecretKeySpec(secret.toByteArray().copyOf(32), "AES")
val iv = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
val cipher = Cipher.getInstance("AES/GCM/NoPadding")
cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
val encrypted = cipher.doFinal(plaintext.toByteArray())
println(Base64.getEncoder().encodeToString(iv + encrypted))
EOF

kotlinc-jvm -script /tmp/encrypt.kts -- "<AI_API_KEY_ENCRYPTION_SECRET>" "<твой_api_ключ>"
```

Полученное зашифрованное значение вставь в БД:

```sql
-- Обновить провайдера (провайдер 'default' уже создан миграцией V2)
UPDATE ai_providers
SET api_key_enc = '<зашифрованное значение>',
    base_url    = 'https://api.твой-провайдер.com',
    enabled     = true
WHERE id = 'default';

-- Включить модели
UPDATE model_configs SET enabled = true WHERE provider_id = 'default';

-- Добавить кастомные модели (опционально)
INSERT INTO model_configs (id, provider_id, display_name, context_window, supports_streaming, enabled)
VALUES ('my-model-id', 'default', 'My Model', 128000, true, true)
ON CONFLICT DO NOTHING;
```

---

## Шаг 9 — Настроить Flutter-приложение

При сборке Flutter-приложения передай адрес бэкенда через `--dart-define`:

```bash
# macOS
~/Downloads/flutter/bin/flutter build macos \
  --dart-define=API_BASE_URL=https://api.okak.club \
  --dart-define=WS_BASE_URL=wss://api.okak.club

# Android
~/Downloads/flutter/bin/flutter build apk \
  --dart-define=API_BASE_URL=https://api.okak.club \
  --dart-define=WS_BASE_URL=wss://api.okak.club

# iOS
~/Downloads/flutter/bin/flutter build ios \
  --dart-define=API_BASE_URL=https://api.okak.club \
  --dart-define=WS_BASE_URL=wss://api.okak.club

# Web
~/Downloads/flutter/bin/flutter build web \
  --dart-define=API_BASE_URL=https://api.okak.club \
  --dart-define=WS_BASE_URL=wss://api.okak.club
```

> `wss://` — WebSocket over TLS (соответствует `https://`). Обязателен для продакшена.

---

## Авто-деплой при пуше

Dokploy поддерживает webhook от GitHub для автоматического передеплоя:

1. Stack → вкладка **General** → секция **Auto Deploy** → включи
2. GitHub автоматически настроит webhook на репо
3. Каждый пуш в `main` запустит пересборку и перезапуск

---

## Частые проблемы

### Gradle билд падает с "Could not resolve"

Gradle не может скачать зависимости — проблема с сетью или отсутствие интернет-доступа из контейнера. Проверь firewall на сервере.

### Postgres не поднялся, сервисы падают на старте

Healthcheck postgres не прошёл. Проверь логи postgres-контейнера:
```bash
# Dokploy: Stack → postgres → Logs
```

Типичная причина: неверный `POSTGRES_PASSWORD` или нехватка места на диске.

### Let's Encrypt не выдаёт сертификат

Убедись что:
- DNS `A`-запись `api.okak.club` указывает на IP сервера
- Порт 80 и 443 открыты в firewall сервера
- Traefik запущен (проверь через `docker ps` на сервере)

### WebSocket соединения разрываются

Nginx на проксировании WebSocket требует `proxy_read_timeout`. Он уже установлен в `nginx.conf` на 120 секунд. Если нужно больше — отредактируй и задеплой заново.

### Flyway миграция упала

Если поменял схему и хочешь пересоздать БД:
```bash
# ОСТОРОЖНО: удаляет все данные
docker volume rm okakchat_pgdata
# Затем Redeploy в Dokploy
```

---

## Обновление

```bash
# Локально
git add .
git commit -m "feat: ..."
git push origin main

# Dokploy автоматически пересоберёт и задеплоит (если включён Auto Deploy)
# Или вручную: Stack → Deploy
```

Миграции Flyway применяются автоматически при старте каждого сервиса.

---

## Структура сети

```
Интернет
    │ HTTPS/WSS
Traefik (Dokploy)  ← SSL termination, Let's Encrypt
    │ HTTP
nginx:80  ← routing по path
    ├── /api/auth/*   → auth-service:8081
    ├── /api/admin/*  → auth-service:8081
    ├── /api/chat/*   → chat-service:8082
    └── /api/ai/*     → ai-proxy:8083  (WebSocket)
                             │
                       PostgreSQL:5432
```

Все сервисы находятся в одной Docker-сети Stack'а и общаются по именам контейнеров. Наружу торчит только nginx через Traefik.
