import os
root = r"app/src/main"
fixed = []
for dirpath, _, files in os.walk(root):
    for f in files:
        if not f.lower().endswith((".xml", ".kt", ".kts")):
            continue
        p = os.path.join(dirpath, f)
        data = open(p, "rb").read()
        if data.startswith(b"\xef\xbb\xbf"):
            open(p, "wb").write(data[3:])
            fixed.append(p)
print("снят BOM:", len(fixed))
for x in fixed: print(" ", x)
