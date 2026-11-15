import os
import datetime

MEM = r"C:\Users\rioor\.memory"
TODAY = "2026-11-14"

page = os.path.join(MEM, "projects", "active", "clockweather-android.md")
os.makedirs(os.path.dirname(page), exist_ok=True)

page_text = f"""---
title: «Прозрачные часы и погода» — Android-виджеты
date: {TODAY}
type: project
salience: 1
last_access: {TODAY}
tags: [android, kotlin, widgets, weather, open-meteo]
summary: Прозрачные часы+погода: 3 виджета, Open-Meteo, уведомления
---

# «Прозрачные часы и погода» (ClockWeather)

Клон Transparent Clock & Weather 9.19.0 под БРАТИКА, собран с нуля 14.11.2026.

- Проект: `C:\\Users\\rioor\\Documents\\DeepSeek Harness\\ClockWeather`
- Пакет: `com.bratik.clockweather`, minSdk 32 / targetSdk 36; APK ~11.6 МБ,
  `dist/ClockWeather-1.0.apk`, ставится на CPH2745 (Android 17 / SDK 37).
- Виджеты 2×1, 4×1, 4×2 (прозрачная подложка 0–100%, Roboto Thin/Light,
  свои PNG-иконки на 7 состояний). Часы — AlarmManager на границу минуты,
  погода — WorkManager (15–180 мин), тап = обновить.
- Погода: Open-Meteo (forecast + geocoding), уведомления о дожде/снеге с
  антиспамом, тихими часами и порогами в настройках.
- Тесты: 42 JUnit-теста (Format, AlertRules, OpenMeteoParser) — зелёные.
- Отчёт со скриншотами: `ClockWeather/README.md`, `docs/screenshots/`.

## Что выяснилось на устройстве

- ViewBinding для `activity_settings.xml` — класс `ActivitySettingsBinding`
  (префикс `Layout` только у DataBinding).
- Material `Slider`: `value` обязан быть кратен `stepSize`, иначе
  `IllegalStateException` прямо при инфляции → краш экрана.
- RemoteViews: вложенный `LinearLayout` с `layout_weight` в виджете на ColorOS
  молча терял левую колонку (время/город/дата). RelativeLayout с якорями работает.
- `pm grant` на ColorOS запрещён оболочкой — разрешение выдаётся только через диалог.
- PowerShell `Set-Content -Encoding utf8` после `Get-Content -Raw` дважды
  кодирует кириллицу (мохабра). Надёжно: `[System.IO.File]::ReadAllText/WriteAllText`
  с `UTF8Encoding($false)` или python.

## Ограничения

- Геолокация не реализована (только поиск города), ночной набор иконок не рисовался,
  естественный триггер уведомления в Киеве не поймать (вероятность осадков была 3%) —
  проверяли искусственным снимком.
"""

open(page, "wb").write(page_text.encode("utf-8"))

# index: вставить строку в раздел projects перед «DshApp»
index = os.path.join(MEM, "index.md")
lines = open(index, "rb").read().decode("utf-8").splitlines()
entry = ("- [«Прозрачные часы и погода»…](projects/active/clockweather-android.md) — "
         "Виджеты часы+погода: 3 размера, Open-Meteo, уведомления `salience:1`")
if "clockweather-android.md" not in "\n".join(lines):
    out = []
    for ln in lines:
        if ln.startswith("- [DshApp]"):
            out.append(entry)
            out.append("")
        out.append(ln)
    open(index, "wb").write(("\n".join(out) + "\n").encode("utf-8"))
    print("index updated")

# log
log = os.path.join(MEM, "log.md")
entry_log = f"""
## [{TODAY}] project | «Прозрачные часы и погода» — собран и проверен на CPH2745
- Сделано приложение ClockWeather (Kotlin, minSdk 32): 3 прозрачных виджета 2×1/4×1/4×2,
  Roboto Thin/Light, свои фото-реалистичные иконки (7 состояний), минутный тик через
  AlarmManager, погода Open-Meteo через WorkManager, уведомления о дожде/снеге с
  антиспамом и тихими часами, настройки на русском.
- 42 юнит-теста зелёные; APK 11.6 МБ установлен, все три виджета живут на рабочем столе,
  уведомление показано, прозрачность 0–100% работает. Отчёт: `ClockWeather/README.md`.
- Грабли (записаны в skills/android-kotlin-in-dsh.md): RemoteViews теряет вложенные
  LinearLayout с weight → RelativeLayout; Slider падает при значении не кратном stepSize;
  ViewBinding без префикса Layout; PowerShell дважды кодирует кириллицу.
"""
with open(log, "ab") as f:
    f.write(entry_log.encode("utf-8"))
print("log appended")

# skill: дописать грабли
skill = os.path.join(MEM, "skills", "android-kotlin-in-dsh.md")
addon = """
## 2026-11-14 (ClockWeather): RemoteViews, ViewBinding, кодировка

- **RemoteViews + вложенный LinearLayout с `layout_weight`** (ColorOS/Android 17):
  левая колонка виджета (город/время/дата) молча пропала, при этом текст в
  RelativeLayout-блоке рисовался. Лечение — только относительные якоря
  (`layout_toStartOf`, `layout_alignParentEnd`), без весов и `width=0dp`.
- **Material `Slider`**: `android:value` обязан быть кратен `android:stepSize`,
  иначе `IllegalStateException: Value(18.0) must be equal to valueFrom(0.0) plus a
  multiple of stepSize(5.0)` **при инфляции** — экран падает до onCreate. В коде
  тоже приводить: `(value / step * step)`.
- **ViewBinding**: для `activity_settings.xml` класс называется `ActivitySettingsBinding`,
  префикс `Layout` бывает только у DataBinding. Ошибка «Unresolved reference
  LayoutActivitySettingsBinding» даёт 90+ каскадных ошибок в одном файле.
- **PowerShell `Get-Content -Raw` + `Set-Content -Encoding utf8`** дважды кодирует
  кириллицу (UTF-8 → cp1251 mojibake, «РљРёРµРІ»), а `Set-Content -Encoding utf8`
  ещё и добавляет BOM → AAPT «Cannot read field elmName». Надёжно:
  `[System.IO.File]::ReadAllText/WriteAllText($p, $t, (New-Object System.Text.UTF8Encoding($false)))`,
  либо python. Чинить существующее: `text.encode("cp1251").decode("utf-8")`.
- **`pm grant` на ColorOS** запрещён («Neither user 2000 nor current process has
  GRANT_RUNTIME_PERMISSIONS») — POST_NOTIFICATIONS выдаётся только системным диалогом
  из самой активности.
- **Проверка уведомлений без подходящей погоды**: временный exported-receiver в debug,
  который подкладывает fake-снимок и зовёт Notifier; после проверки удалить вместе с
  записью в манифесте и пересобрать.
"""
with open(skill, "ab") as f:
    f.write(addon.encode("utf-8"))
print("skill appended")
