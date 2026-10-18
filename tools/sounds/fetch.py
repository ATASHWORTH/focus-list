"""Runs on GitHub Actions: catalogs freely licensed chicken/rooster audio on Wikimedia Commons, downloads short
candidates, and saves 20-second mono MP3 previews plus license data to tools/sounds/candidates/."""
import json, os, re, subprocess, urllib.parse, urllib.request, zipfile, io

UA = {"User-Agent": "FocusListSoundFetcher/1.0 (https://github.com/ATASHWORTH/focus-list)"}
API = "https://commons.wikimedia.org/w/api.php"
OUT = "tools/sounds/candidates"
os.makedirs(OUT, exist_ok=True)

def get(url):
    return urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60).read()

def api(**p):
    p.update(format="json")
    return json.loads(get(API + "?" + urllib.parse.urlencode(p)))

cats = ["Category:Audio files of Gallus gallus", "Category:Audio files of Gallus gallus domesticus",
        "Category:Roosters crowing", "Category:Sounds of chickens", "Category:Chicken sounds"]
seen, titles = set(), []
def walk(cat, depth):
    if cat in seen or depth > 2: return
    seen.add(cat)
    cont = {}
    while True:
        r = api(action="query", list="categorymembers", cmtitle=cat, cmlimit=500, **cont)
        for m in r.get("query", {}).get("categorymembers", []):
            t = m["title"]
            if t.startswith("Category:"): walk(t, depth + 1)
            elif re.search(r"\.(ogg|oga|wav|mp3|flac|opus|webm)$", t, re.I): titles.append(t)
        if "continue" not in r: break
        cont = {"cmcontinue": r["continue"]["cmcontinue"]}
for c in cats: walk(c, 0)
# keyword search too
for q in ["rooster crowing", "hen clucking", "chicken clucking", "chickens sound", "cock crow"]:
    r = api(action="query", list="search", srsearch=q + " filetype:audio", srnamespace=6, srlimit=50)
    titles += [m["title"] for m in r.get("query", {}).get("search", [])]
titles = list(dict.fromkeys(titles))

catalog = []
for i in range(0, len(titles), 40):
    r = api(action="query", prop="imageinfo", titles="|".join(titles[i:i+40]),
            iiprop="url|size|mime|extmetadata|mediatype", iiextmetadatafilter="LicenseShortName|Artist|ObjectName|ImageDescription|Credit")
    for p in r.get("query", {}).get("pages", {}).values():
        ii = (p.get("imageinfo") or [{}])[0]
        md = ii.get("extmetadata", {})
        strip = lambda k: re.sub("<[^>]+>", "", md.get(k, {}).get("value", "")).strip()[:300]
        catalog.append({"title": p["title"], "url": ii.get("url"), "page": ii.get("descriptionurl"), "bytes": ii.get("size"),
                        "license": strip("LicenseShortName"), "artist": strip("Artist"), "desc": strip("ImageDescription")})

def preview(src_bytes, name):
    raw = f"/tmp/{name}.src"
    open(raw, "wb").write(src_bytes)
    probe = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", raw], capture_output=True, text=True)
    dur = float(probe.stdout.strip() or 0)
    subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", raw, "-t", "20", "-ac", "1", "-ar", "22050", "-b:a", "64k", f"{OUT}/{name}.mp3"], check=True)
    return dur

n = 0
for c in catalog:
    if not c["url"] or (c["bytes"] or 0) > 12_000_000: continue
    if not re.search(r"(cc0|public domain|cc by|cc-by|pd)", c["license"], re.I): continue
    name = f"c{n:02d}"
    try:
        c["duration"] = preview(get(c["url"]), name); c["file"] = name + ".mp3"; n += 1
    except Exception as e:
        c["error"] = str(e)[:200]

# OpenGameArt "Chicken Sound Effect" by IMadeIt, CC-BY 3.0
try:
    z = zipfile.ZipFile(io.BytesIO(get("https://opengameart.org/sites/default/files/chicken_sound_effect.zip")))
    for j, zn in enumerate(x for x in z.namelist() if re.search(r"\.(ogg|wav|mp3|flac)$", x, re.I)):
        name = f"oga{j}"
        d = preview(z.read(zn), name)
        catalog.append({"title": zn, "page": "https://opengameart.org/content/chicken-sound-effect", "license": "CC-BY 3.0",
                        "artist": "IMadeIt", "duration": d, "file": name + ".mp3"})
except Exception as e:
    catalog.append({"title": "opengameart chicken", "error": str(e)[:200]})

json.dump(catalog, open(f"{OUT}/catalog.json", "w"), indent=1)
print(len(catalog), "catalogued,", n, "previews")
