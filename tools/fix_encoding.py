"""Чинит двойную кодировку (UTF-8 -> cp1251 mojibake) в исходниках проекта."""
import os
import re

ROOT = r"C:\Users\rioor\Documents\DeepSeek Harness\ClockWeather\app\src"
SUSPECT = re.compile(r"(Р[а-яё]|[Сс][Рр]|вЂ|Рѕ|РµР)")


def repair(text):
    try:
        fixed = text.encode("cp1251").decode("utf-8")
    except (UnicodeEncodeError, UnicodeDecodeError):
        return None
    return fixed


changed = []
for dirpath, _, files in os.walk(ROOT):
    for name in files:
        if not name.lower().endswith((".kt", ".xml", ".kts")):
            continue
        path = os.path.join(dirpath, name)
        data = open(path, "rb").read()
        if data.startswith(b"\xef\xbb\xbf"):
            data = data[3:]
        try:
            text = data.decode("utf-8")
        except UnicodeDecodeError:
            print("!! не UTF-8:", path)
            continue
        hits = len(SUSPECT.findall(text))
        if hits < 3:
            continue
        fixed = repair(text)
        if fixed is None:
            continue
        # после починки «русского мусора» быть не должно
        if len(SUSPECT.findall(fixed)) >= hits:
            continue
        open(path, "wb").write(fixed.encode("utf-8"))
        changed.append((path, hits))

print("исправлено файлов:", len(changed))
for p, n in changed:
    print(f"  {n:4d} маркеров  {p}")
