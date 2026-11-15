"""Ищет остатки двойной кодировки (UTF-8 как cp1251) и чинит их."""
import os

ROOT = r"C:\Users\rioor\Documents\DeepSeek Harness\ClockWeather\app\src"

# Типичные артефакты: кириллица из диапазона «Р/С + знаки», плюс U+00B7 в паре с В
MARKERS = ["\u0420\u0451", "\u0420\u0455", "\u0420\u0452", "\u0420\u0491",
           "\u0420\u0405", "\u0421\u201a", "\u0420\u00b5", "\u0412\u00b7",
           "\u0420\u0406", "\u0421\u0403", "\u0420\u00bb", "\u0420\u045c"]


def repair(text):
    try:
        return text.encode("cp1251").decode("utf-8")
    except (UnicodeEncodeError, UnicodeDecodeError):
        return None


def markers(text):
    return sum(text.count(m) for m in MARKERS)


fixed_files, manual = [], []
for dirpath, _, files in os.walk(ROOT):
    for name in files:
        if not name.lower().endswith((".kt", ".xml", ".kts")):
            continue
        path = os.path.join(dirpath, name)
        raw = open(path, "rb").read()
        if raw.startswith(b"\xef\xbb\xbf"):
            raw = raw[3:]
        text = raw.decode("utf-8", "replace")
        hits = markers(text)
        if hits == 0:
            continue
        fixed = repair(text)
        if fixed is not None and markers(fixed) < hits:
            open(path, "wb").write(fixed.encode("utf-8"))
            fixed_files.append((path, hits, markers(fixed)))
        else:
            manual.append((path, hits))

print("починено:", len(fixed_files))
for p, before, after in fixed_files:
    print(f"  markers {before} -> {after}  {p.split('clockweather')[-1]}")
if manual:
    print("требуют глаз:")
    for p, n in manual:
        print(f"  markers={n}  {p.split('clockweather')[-1]}")
