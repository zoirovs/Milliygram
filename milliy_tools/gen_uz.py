"""Rasmiy o'zbekcha Telegram tarjimasidan (uz_langpack.json) Milliygram til fayllarini yasaydi:
  values-uz/strings.xml        — lotin
  values-b+uz+Cyrl/strings.xml — kirill (avtomatik o'girilgan)
Placeholder'lari inglizchasiga mos kelmagan matnlar tashlab ketiladi (inglizchasi ishlatiladi).
Ishlatish: python3 milliy_tools/gen_uz.py"""
import json, re, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "TMessagesProj/src/main/res"
PH = re.compile(r"%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z]|%%|\bun\d\b")
PROTECT = re.compile(r"%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z]|%%|\bun\d\b|https?://\S+|tg://\S+|<[^>]+>|@[A-Za-z0-9_]+|\{[^}]*\}|\b(?:2FA|APK|CVV|QR|GIF|ID|URL|API|PDF|HD|NFT|TON|USDT|KB|MB|GB|TB|IP|DC|VPN|AI|SIM|iOS|PIN|FAQ|DNS|SSL|HTTP|HTTPS|JSON|PNG|JPG|MP3|MP4|UTC|GMT|Wi-Fi|Bluetooth|Android|Google|Apple|Fragment|Ghost|Stars)\b")

def unescape(v):
    return v.replace("\\n", "\n").replace("\\", "").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&").replace("&quot;", '"')

def escape(v):
    v = v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    v = v.replace("\\", "\\\\").replace("'", "\\'").replace('"', '\\"').replace("\n", "\\n")
    if v[:1] in ("@", "?"):
        v = "\\" + v
    return v

APOS = set("'ʻʼ‘’`")
def to_cyr_plain(t):
    out, i, n = [], 0, len(t)
    lat = lambda ch: ch.isascii() and ch.isalpha()
    M = dict(a="а", b="б", c="с", d="д", f="ф", g="г", h="ҳ", i="и", j="ж", k="к", l="л", m="м", n="н", o="о",
             p="п", q="қ", r="р", s="с", t="т", u="у", v="в", w="в", x="х", y="й", z="з")
    while i < n:
        c = t[i]; lc = c.lower(); up = c != lc
        nx = t[i + 1].lower() if i + 1 < n else " "
        pv = t[i - 1] if i > 0 else " "
        o, skip = None, 0
        if lc in "og" and nx in APOS:
            o, skip = ("ў" if lc == "o" else "ғ"), 1
        elif lc == "s" and nx == "h": o, skip = "ш", 1
        elif lc == "c" and nx == "h": o, skip = "ч", 1
        elif lc == "y" and nx in "ouae":
            if nx == "o" and i + 2 < n and t[i + 2] in APOS: o = "й"
            else: o, skip = {"o": "ё", "u": "ю", "a": "я", "e": "е"}[nx], 1
        elif c in APOS:
            out.append(("Ъ" if pv.isupper() else "ъ") if (lat(pv) and i + 1 < n and lat(t[i + 1])) else c); i += 1; continue
        elif lat(c):
            o = ("е" if lat(pv) else "э") if lc == "e" else M.get(lc)
        if o is None:
            out.append(c); i += 1; continue
        if up:
            nxt_up = (i + 1 + skip < n and t[i + 1 + skip].isupper())
            o = o.upper() if (len(o) == 1 or nxt_up) else o[0].upper() + o[1:]
        out.append(o); i += 1 + skip
    return "".join(out)

def to_cyr(t):
    parts, last = [], 0
    for m in PROTECT.finditer(t):
        parts.append(to_cyr_plain(t[last:m.start()])); parts.append(m.group(0)); last = m.end()
    parts.append(to_cyr_plain(t[last:]))
    return "".join(parts)

DATE_KEY = re.compile(r"^(formatter|chatDate|chatFullDate|formatDateSchedule)")
DATE_LETTERS = set("GyMdHhmsSEaKkLuz")

def fix_date_pattern(v):
    """Sana formati (SimpleDateFormat): so'zlarni ('yil') qo'shtirnoqqa oladi, noto'g'ri bo'lsa None.
    Kirillga O'GIRILMAYDI — oy nomlarini Java locale o'zi kirillda beradi."""
    v = v.strip().replace("mmm", "MMM")  # tarjimadagi xato: dd-mmm (daqiqa emas, oy)
    v = re.sub(r"(?<!')\byil\b(?!')", "'yil'", v)
    outside = re.sub(r"'[^']*'", "", v)
    if any(ch.isalpha() and ch not in DATE_LETTERS for ch in outside):
        return None
    return v

def main():
    en_raw = (RES / "values/strings.xml").read_text(encoding="utf-8")
    en = {k: unescape(v) for k, v in re.findall(r'<string name="([^"]+)"[^>]*>(.*?)</string>', en_raw, re.S)}
    uz = json.loads((ROOT / "milliy_tools/uz_langpack.json").read_text(encoding="utf-8"))["strings"]
    lat, cyr, bad = [], [], 0
    for k in en:
        if k in ("AppName", "AppNameBeta"):
            continue
        if k.startswith("Milliy"):  # o'z matnlarimiz: asli lotincha, kirillga o'giramiz
            cyr.append((k, to_cyr(en[k])))
            continue
        v = uz.get(k)
        if v is None:
            continue
        v = v.replace("<![CDATA[", "").replace("]]>", "")  # tarjimada matn sifatida kelgan CDATA belgilari
        if sorted(PH.findall(v)) != sorted(PH.findall(en[k])):
            bad += 1; continue
        v = v.replace("Telegram", "Milliygram") if k in ("AppNameFull",) else v
        if DATE_KEY.match(k) and "%" not in en[k]:
            v = fix_date_pattern(v)
            if v is None:
                bad += 1; continue
            lat.append((k, v)); cyr.append((k, v.replace("'yil'", "'йил'")))
            continue
        lat.append((k, v)); cyr.append((k, to_cyr(v)))
    for d, items in (("values-uz", lat), ("values-b+uz+Cyrl", cyr)):
        p = RES / d; p.mkdir(exist_ok=True)
        body = "".join(f'    <string name="{k}">{escape(v)}</string>\n' for k, v in items)
        (p / "strings.xml").write_text('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n' + body + "</resources>\n", encoding="utf-8")
    print(f"en={len(en)} uz_tayyor={len(lat)} placeholder_mos_emas={bad} yetishmaydi={len(en) - len(lat) - bad}")

if __name__ == "__main__":
    main()
