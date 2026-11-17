"""Digest сессии: ClockWeather 1.4–1.7, GitHub-релизы, иконки через OpenRouter."""
import os

MEM = r"C:\Users\rioor\.memory"
TODAY = "2026-11-16"

page = os.path.join(MEM, "projects", "active", "clockweather-android.md")
open(page, "wb").write(f"""---
title: «Прозрачные часы и погода» — Android-виджеты
date: 2026-11-14
type: project
salience: 1
last_access: {TODAY}
tags: [android, kotlin, widgets, weather, open-meteo, github-releases]
summary: Часы+погода: 4 виджета, экран погоды, автообновление с GitHub
---

# «Прозрачные часы и погода» (ClockWeather)

Клон Transparent Clock & Weather 9.19.0 под БРАТИКА, собран с нуля 14.11.2026.

- Проект: `C:\\Users\\rioor\\Documents\\DeepSeek Harness\\ClockWeather`
- Репозиторий (публичный): https://github.com/rioorob-hash2/ClockWeather-Android
- Пакет: `com.bratik.clockweather`, minSdk 32 / targetSdk 36, Kotlin 2.0.21, AGP 8.7.3.
- Виджеты **2×1, 4×1, 4×2, 4×3** (4×3 — часы + текущая погода + прогноз на 5 дней),
  прозрачная подложка 0–100 %, Roboto Thin/Light, свои PNG-иконки на 7 состояний.
  Часы — AlarmManager на границу минуты, погода — WorkManager (15–180 мин).
- Тап по любому виджету открывает **экран погоды** (WeatherActivity): лента 12 часов
  с ломаной температур и 7 дней с вероятностью осадков и шкалой min—max.
- Погода: Open-Meteo (forecast 7 дней + hourly + geocoding), уведомления о дожде/снеге
  с антиспамом, тихими часами и порогами.
- **Проверка обновлений приложения** (1.5+): карточка «Обновления» в настройках читает
  `releases/latest` этого репозитория, сравнивает версии, кнопка ведёт на APK из релиза;
  автопроверка не чаще раза в сутки.
- Тесты: 58 JUnit-тестов (Format, AlertRules, OpenMeteoParser, Version, UpdateParser) — зелёные.
- APK не в git (`.gitignore`), лежит в `dist/` и в GitHub Releases (v1.5, v1.6, v1.7).

## Выпуск релиза (процедура)

1. Поднять `versionCode`/`versionName` в `app/build.gradle.kts`, собрать `tools/run.ps1 apk`.
2. `git commit`, затем `tools/publish_github.py` (заливает файлы через API — git push
   из песочницы иногда не тянет HTTPS).
3. Создать Release с тегом `vX.Y` и ассетом APK через API — приложение увидит его
   при следующей проверке.

## Что выяснилось на устройстве

- ViewBinding для `activity_settings.xml` — класс `ActivitySettingsBinding`
  (префикс `Layout` только у DataBinding).
- Material `Slider`: `value` обязан быть кратен `stepSize`, иначе `IllegalStateException`
  прямо при инфляции → краш экрана.
- RemoteViews: вложенный `LinearLayout` с `layout_weight` молча теряет колонку на ColorOS.
  Лечение — RelativeLayout с якорями; полосу прогноза 5 дней вообще рисуем в Bitmap
  (`ForecastStrip.kt`).
- `pm grant` на ColorOS запрещён — разрешения только через системный диалог.
- PowerShell `Set-Content -Encoding utf8` после `Get-Content -Raw` дважды кодирует
  кириллицу. Надёжно: `[System.IO.File]::ReadAllText/WriteAllText` с `UTF8Encoding($false)`.

## Иконки: Google 402 → OpenRouter

Ключ Google image-gen упёрся в «prepayment credits depleted» (402). В `~/.dsh/.credentials.yaml`
лежит ключ OpenRouter (лимит $50, расход <$1). Через `openrouter.ai/api/v1/chat/completions`
с `model=google/gemini-3.1-flash-image`, `modalities=["image","text"]` и референс-картинкой
в `image_url` генерируется иконка за **~$0.068**. Облако «облачно» переделано так:
референс тёмного дождевого облака → «same silhouette, remove drops, light white cumulus,
pure magenta background», затем вырезание фона (`tools/cutout_cloud.py`).

## Версии

- **1.2**: 4×3 виджет + экран погоды по тапу.
- **1.3**: температура вплотную к иконке в 4×2.
- **1.4**: в 4×3 градусы перенесены под иконку (больше не наезжают на время).
- **1.5**: проверка обновлений через GitHub Releases.
- **1.6**: светлая иконка «облачно» формы тёмных (без «ваты»), сделана из `wx_rain`.
- **1.7**: облако перегенерировано через OpenRouter — объёмнее и аккуратнее.

## Ограничения

- Геолокация не реализована (только поиск города); ночных иконок нет.
- Естественный триггер уведомления поймать не удалось (в Одессе вероятность была 2–3 %),
  проверяли искусственным снимком.
- Установка APK прямо из приложения не сделана — «Скачать» открывает браузер.
""".encode("utf-8"))

log = os.path.join(MEM, "log.md")
with open(log, "ab") as f:
    f.write(f"""
## [{TODAY}] project | ClockWeather: релизы, автообновление, иконки через OpenRouter
- Приложение выложено в публичный GitHub `rioorob-hash2/ClockWeather-Android` (исходники + скриншоты JPG + README, без APK и keystore).
- Добавлена проверка обновлений: карточка в настройках читает `releases/latest`, сравнивает версии, кнопка ведёт на APK из релиза; автопроверка раз в сутки. Проверено на CPH2745 (1.5 → «установлена последняя версия»).
- Выпуски v1.5, v1.6, v1.7 с APK в Releases; 58 тестов зелёные.
- Иконка «облачно» переделана: Google image-gen отдал 402 (кредиты), сгенерировал через OpenRouter `google/gemini-3.1-flash-image` (~$0.068) по референсу тёмного облака.
- Записан скилл: OpenRouter как запасной канал для image-gen в DSH.
""".encode("utf-8"))

skill = os.path.join(MEM, "skills", "image-video-generation.md")
if os.path.exists(skill):
    with open(skill, "ab") as f:
        f.write("""
## 2026-11-16: Google 402 → OpenRouter как запасной канал

- Симптом: `edit_image`/`generate_image` через провайдер google падает
  `402 RESOURCE_EXHAUSTED: Your prepayment credits are depleted`.
- Ключ OpenRouter лежит в `~/.dsh/.credentials.yaml` (`sk-or-v1-...`), лимит $50.
- Работает напрямую через `POST https://openrouter.ai/api/v1/chat/completions`:
  `{"model": "google/gemini-3.1-flash-image", "modalities": ["image","text"],
    "messages": [{"role":"user","content":[{"type":"text","text":...},
    {"type":"image_url","image_url":{"url":"data:image/png;base64,..."}}]}]}`
  Картинка приходит в `choices[0].message.images[0].image_url.url` (base64), стоимость
  иконки 1024×1024 ≈ **$0.068** (`usage.cost`).
- Список моделей с выводом картинок: `GET /api/v1/models`, фильтр
  `architecture.output_modalities` содержит `image` (13 моделей на 16.11.2026).
- Референс-картинка сохраняет форму: «keep this exact silhouette, remove X, repaint as ...,
  pure magenta background» — и дальше обычное вырезание фона по magenta.
- Скрипты в проекте ClockWeather: `tools/gen_cloud_openrouter.py`, `tools/cutout_cloud.py`.
""".encode("utf-8"))
    print("skill updated")

# index: обновить summary проекта
index = os.path.join(MEM, "index.md")
lines = open(index, "rb").read().decode("utf-8").splitlines()
out = []
for ln in lines:
    if "clockweather-android.md" in ln:
        ln = ("- [«Прозрачные часы и погода»…](projects/active/clockweather-android.md) — "
              "Часы+погода: 4 виджета, экран, автообновление `salience:1`")
    out.append(ln)
open(index, "wb").write(("\n".join(out) + "\n").encode("utf-8"))
print("digest ok")
