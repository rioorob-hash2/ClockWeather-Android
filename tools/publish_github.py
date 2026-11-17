"""Публикация проекта ClockWeather в публичный GitHub-репозиторий через API.

Токен читается из gh_token.txt и не печатается. Работает без git push
(на этой машине git из песочницы не всегда тянет HTTPS).
"""
import base64
import json
import os
import subprocess
import sys
import urllib.error
import urllib.request

ROOT = r"C:\Users\rioor\Documents\DeepSeek Harness\ClockWeather"
TOKEN = open(
    r"C:\Users\rioor\Documents\DeepSeek Harness\gh_token.txt", encoding="utf-8"
).read().strip()
OWNER = "rioorob-hash2"
REPO = "ClockWeather-Android"
BRANCH = "main"
API = "https://api.github.com"


def call(method, path, payload=None):
    url = path if path.startswith("http") else API + path
    data = json.dumps(payload).encode() if payload is not None else None
    req = urllib.request.Request(url, data=data, method=method, headers={
        "Authorization": "Bearer " + TOKEN,
        "Accept": "application/vnd.github+json",
        "Content-Type": "application/json",
        "User-Agent": "dsh-clockweather",
    })
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            body = r.read().decode()
            return r.status, (json.loads(body) if body else {})
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read().decode() or "{}")


def tracked_files():
    out = subprocess.run(["git", "-C", ROOT, "ls-files"], capture_output=True, text=True, check=True)
    return [p for p in out.stdout.splitlines() if p.strip()]


def main():
    status, user = call("GET", "/user")
    if status != 200:
        sys.exit(f"токен не работает: {status}")
    print("user:", user["login"])

    status, repo = call("POST", "/user/repos", {
        "name": REPO,
        "private": False,
        "description": "Прозрачные часы и погода — Android-виджеты 2x1/4x1/4x2/4x3 на Kotlin, Open-Meteo, без трекеров",
        "has_issues": True,
        "has_wiki": False,
    })
    if status == 201:
        print("репозиторий создан:", repo["html_url"])
    elif status == 422:
        print("репозиторий уже существует, обновляю содержимое")
    else:
        sys.exit(f"не удалось создать репозиторий: {status} {repo}")

    readme = open(os.path.join(ROOT, "README.md"), "rb").read()
    # README уже может существовать — тогда GitHub требует sha для обновления.
    st, existing = call("GET", f"/repos/{OWNER}/{REPO}/contents/README.md?ref={BRANCH}")
    readme_payload = {
        "message": "README: обновление описания",
        "content": base64.b64encode(readme).decode(),
        "branch": BRANCH,
    }
    if st == 200 and isinstance(existing, dict) and existing.get("sha"):
        readme_payload["sha"] = existing["sha"]
    status, seed = call("PUT", f"/repos/{OWNER}/{REPO}/contents/README.md", readme_payload)
    if status not in (200, 201):
        sys.exit(f"seed README не удался: {status} {seed}")
    base_tree = seed["commit"]["tree"]["sha"]
    parent = seed["commit"]["sha"]
    print("seed commit:", parent[:7])

    entries = []
    files = tracked_files()
    for i, rel in enumerate(files, 1):
        if rel == "README.md":
            continue
        path = os.path.join(ROOT, rel.replace("/", os.sep))
        raw = open(path, "rb").read()
        st, blob = call("POST", f"/repos/{OWNER}/{REPO}/git/blobs", {
            "content": base64.b64encode(raw).decode(),
            "encoding": "base64",
        })
        if st != 201:
            print("  ! blob fail", rel, st)
            continue
        entries.append({"path": rel, "mode": "100755" if rel.endswith(".sh") else "100644",
                        "type": "blob", "sha": blob["sha"]})
        if i % 20 == 0:
            print(f"  ... {i}/{len(files)}")

    st, tree = call("POST", f"/repos/{OWNER}/{REPO}/git/trees", {
        "base_tree": base_tree, "tree": entries,
    })
    if st != 201:
        sys.exit(f"tree fail: {st} {tree}")

    # Сообщение берём из последнего локального коммита (раньше было захардкожено «1.4»).
    try:
        msg = subprocess.run(["git", "log", "-1", "--pretty=%B"], capture_output=True,
                             text=True, encoding="utf-8", cwd=os.path.dirname(os.path.dirname(
                                 os.path.abspath(__file__)))).stdout.strip()
    except Exception:
        msg = ""
    if not msg:
        msg = "ClockWeather: обновление исходников"

    st, commit = call("POST", f"/repos/{OWNER}/{REPO}/git/commits", {
        "message": msg,
        "tree": tree["sha"],
        "parents": [parent],
    })
    if st != 201:
        sys.exit(f"commit fail: {st} {commit}")

    st, ref = call("PATCH", f"/repos/{OWNER}/{REPO}/git/refs/heads/{BRANCH}", {
        "sha": commit["sha"], "force": False,
    })
    print("ref:", st, ref.get("ref", ref))

    st, info = call("GET", f"/repos/{OWNER}/{REPO}")
    print("url:", info.get("html_url"))
    print("private:", info.get("private"), "| size KB:", info.get("size"))
    st, listing = call("GET", f"/repos/{OWNER}/{REPO}/git/trees/{BRANCH}?recursive=1")
    print("файлов в репо:", len(listing.get("tree", [])))


if __name__ == "__main__":
    main()
