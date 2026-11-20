"""Read-only connectivity probe for Mail.ru Music; no audio is downloaded."""
import json
import urllib.parse
import urllib.request
import urllib.error

UA = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 Chrome/130.0 Mobile Safari/537.36"

def fetch(label, address, referer=None, range_header=None, method="GET"):
    headers = {"User-Agent":UA, "Accept-Language":"ru-RU,ru;q=0.9", "Accept":"*/*"}
    if referer:
        headers["Referer"] = referer
    if range_header:
        headers["Range"] = range_header
    if "cgi-bin" in address:
        headers["X-Requested-With"] = "XMLHttpRequest"
        headers["Accept"] = "application/json,text/plain,*/*"
    req = urllib.request.Request(address, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=12) as r:
            data = r.read(220_000 if not range_header else 256)
            content = data.decode("utf-8", errors="replace")
            print(label, "HTTP", r.status, "FINAL_HOST", urllib.parse.urlsplit(r.url).hostname,
                  "MIME",r.headers.get("Content-Type","?"),"BODY_LEN_READ",len(data))
            if "cgi-bin" in address:
                try:
                    obj=json.loads(content)
                    print(label,"JSON_ROOT_TYPE",type(obj).__name__)
                    if isinstance(obj,list):
                        parts=[x for x in obj if isinstance(x,dict)]
                    elif isinstance(obj,dict): parts=[obj]
                    else: parts=[]
                    for item in parts[:2]:
                        print("ENTRY_KEYS", list(item.keys())[:12],
                             "MUSIC_COUNT", len(item.get("MusicData",[])) if isinstance(item.get("MusicData"),list) else "?")
                        m=item.get("MusicData")
                        if isinstance(m,list) and m:
                            first=m[0]
                            print("TRACK_KEYS", list(first.keys())[:25],
                                  "AUDIO_HOST", urllib.parse.urlsplit(first.get("URL","")).hostname)
                except Exception as e:
                    print(label,"NOT_JSON",repr(e),"BODY_PREFIX",repr(content[:120]))
            elif not range_header:
                print(label,"HTML_MARKERS",{
                    "song_link":"/music/songs/" in content,
                    "audio_mp3":"moosic.my.mail.ru/file/" in content,
                    "audio_element":"<audio" in content,
                    "search_cards":"trackTitle" in content,
                })
    except urllib.error.HTTPError as e:
        print(label,"HTTP_ERROR",e.code,"MIME",e.headers.get("Content-Type","?"))
    except Exception as e:
        print(label,"ERROR",type(e).__name__,str(e)[:200])

for label,url in [
    ("MOBILE","https://m.my.mail.ru/music/search/abba"),
    ("DESKTOP","https://my.mail.ru/music/search/abba")
]:
    fetch(label,url)

q={
 "xemail":"","ajax_call":"1","func_name":"music.search","mna":"","mnb":"",
 "arg_query":"abba","arg_extended":"1",
 "arg_search_params":json.dumps({"music":{"limit":5,"offset":0}},separators=(",",":")),
 "arg_limit":5,"arg_offset":0
}
url="https://my.mail.ru/cgi-bin/my/ajax?"+urllib.parse.urlencode(q)
fetch("OLD_AJAX_SEARCH",url, "https://my.mail.ru/music/search/abba")
fetch("SAMPLE_MP3_RANGE","https://moosic.my.mail.ru/file/ebde61a74f061a323d09807823650af1.mp3",
    "https://my.mail.ru/music/",range_header="bytes=0-1")
