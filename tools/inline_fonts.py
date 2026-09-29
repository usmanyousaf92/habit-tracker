#!/usr/bin/env python3
"""
Replaces the Google Fonts <link> in the bundled index.html with an inline
@font-face block whose woff2 payloads are base64 data URIs.

Why: the app is otherwise fully offline, but its typography (Quicksand for
headings, Nunito for body) comes from a CDN. Without this, the first launch on
a plane falls back to the system sans-serif and the fixed 390x844 layout drifts.

This runs in CI, where the network is available. It is deliberately fail-soft:
if anything goes wrong it leaves index.html untouched and exits non-zero so the
workflow logs a skipped step and carries on building.

Usage:  python3 tools/inline_fonts.py
"""

import base64
import os
import re
import sys
import urllib.request

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
INDEX = os.path.join(HERE, "app", "src", "main", "assets", "public", "index.html")

CSS_URL = ("https://fonts.googleapis.com/css2"
           "?family=Quicksand:wght@500;600;700"
           "&family=Nunito:wght@400;600;700;800"
           "&display=swap")

# A browser UA is required or Google serves ttf instead of woff2.
UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")

KEEP_SUBSETS = {"latin", "latin-ext"}

FACE_RE = re.compile(
    r"/\*\s*(?P<subset>[\w-]+)\s*\*/\s*(?P<block>@font-face\s*\{.*?\})",
    re.DOTALL,
)
SRC_RE = re.compile(r"src:\s*url\((?P<url>https://[^)]+\.woff2)\)\s*format\('woff2'\);")


def fetch(url, binary=False):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=30) as r:
        data = r.read()
    return data if binary else data.decode("utf-8")


def main():
    if not os.path.exists(INDEX):
        print(f"not found: {INDEX}", file=sys.stderr)
        return 1

    html = open(INDEX, encoding="utf-8").read()
    if "@font-face" in html and "fonts.googleapis.com" not in html:
        print("fonts already inlined; nothing to do")
        return 0

    css = fetch(CSS_URL)
    faces = []
    total = 0

    for m in FACE_RE.finditer(css):
        if m.group("subset") not in KEEP_SUBSETS:
            continue
        block = m.group("block")
        sm = SRC_RE.search(block)
        if not sm:
            continue
        payload = fetch(sm.group("url"), binary=True)
        total += len(payload)
        b64 = base64.b64encode(payload).decode("ascii")
        faces.append(SRC_RE.sub(
            "src: url(data:font/woff2;base64," + b64 + ") format('woff2');",
            block,
        ))

    if not faces:
        print("no latin woff2 faces parsed from the Google Fonts response",
              file=sys.stderr)
        return 1

    style = "<style>\n/* Fonts inlined at build time by tools/inline_fonts.py */\n" \
            + "\n".join(faces) + "\n</style>"

    # Drop the preconnects and swap the stylesheet link for the inline block.
    html, n_pre = re.subn(
        r'\s*<link rel="preconnect" href="https://fonts\.g[^"]*"[^>]*>', "", html)
    html, n_link = re.subn(
        r'<link rel="stylesheet" href="https://fonts\.googleapis\.com/css2[^"]*">',
        style.replace("\\", "\\\\"), html, count=1)

    if n_link != 1:
        print("could not locate the Google Fonts <link> tag", file=sys.stderr)
        return 1

    open(INDEX, "w", encoding="utf-8").write(html)
    print(f"inlined {len(faces)} faces, {total/1024:.0f} KiB of woff2; "
          f"removed {n_pre} preconnect tags")
    return 0


if __name__ == "__main__":
    sys.exit(main())
