"""Read-only validation of iTunes and Deezer's keyless album metadata APIs."""
import json
import urllib.request
import urllib.parse

UA = "MusicFlow/0.7.8 (GitHub CI; public metadata verification)"

def get(label, endpoint):
    req = urllib.request.Request(endpoint, headers={
        "User-Agent": UA, "Accept":"application/json", "Accept-Language":"en-US,en;q=0.9"
    })
    try:
        with urllib.request.urlopen(req, timeout=12) as r:
            obj=json.load(r)
            print(label, "STATUS", r.status, "ROOT_KEYS", list(obj)[:8])
            return obj
    except Exception as exc:
        print(label, "ERROR", type(exc).__name__, str(exc)[:180])
        return {}

for q in ["Teddy Swims Lose Control", "Кино Группа крови"]:
    enc = urllib.parse.quote(q)
    a = get("ITUNES_SEARCH "+q,
        "https://itunes.apple.com/search?term="+enc+"&media=music&entity=song&limit=25&country=US")
    items = [v for v in a.get("results",[]) if v.get("kind")=="song"]
    print("ITUNES_TRACKS", q, len(items),
          "WITH_ALBUM",sum(bool(x.get("collectionName")) for x in items),
          "WITH_ID",sum(bool(x.get("collectionId")) for x in items))
    if items:
        album = items[0].get("collectionId")
        if album:
            look = get("ITUNES_ALBUM","https://itunes.apple.com/lookup?id="+str(album)+"&entity=song&country=US")
            print("ITUNES_ALBUM_TRACKLIST",len(look.get("results",[])),
                  "TRACK_ITEMS",sum(x.get("wrapperType")=="track" for x in look.get("results",[])))
    d=get("DEEZER_SEARCH "+q,"https://api.deezer.com/search?q="+enc+"&limit=25")
    tracks=d.get("data",[])
    print("DEEZER_TRACKS",q,len(tracks),
          "WITH_ALBUM",sum(bool(x.get("album",{}).get("title")) for x in tracks))
    if tracks:
        album=tracks[0].get("album",{}).get("id")
        if album:
            detail=get("DEEZER_ALBUM","https://api.deezer.com/album/"+str(album))
            print("DEEZER_ALBUM_TRACKLIST",
                  len(detail.get("tracks",{}).get("data",[])),
                  "TITLE_PRESENT",bool(detail.get("title")))
