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

# Test public tracks using the actual JSON keys and report parser exclusion reasons,
# without printing song names, track IDs, URL paths, tokens or user data.
for term in ("abba", "Кино", "Stromae"):
    q2=dict(q)
    q2["arg_query"]=term
    url="https://my.mail.ru/cgi-bin/my/ajax?"+urllib.parse.urlencode(q2)
    req=urllib.request.Request(url,headers={
        "User-Agent":UA,
        "Accept-Language":"ru-RU,ru;q=0.9",
        "Referer":"https://my.mail.ru/music/search/"+urllib.parse.quote(term),
        "X-Requested-With":"XMLHttpRequest",
        "Accept":"application/json,text/plain,*/*"})
    try:
        with urllib.request.urlopen(req,timeout=18) as response:
            payload=json.load(response)
        if isinstance(payload,list):
            objs=[v for v in payload if isinstance(v,dict)]
        elif isinstance(payload,dict): objs=[payload]
        else: objs=[]
        items=next((v.get("MusicData",[]) for v in objs if isinstance(v.get("MusicData"),list)),[])
        counts={"total":len(items),"restricted":0,"file_empty":0,"stream_empty":0,"invalid_https":0,"invalid_host":0,"name_empty":0,"accepted":0}
        for item in items:
            if not isinstance(item,dict):continue
            if item.get("IsRestricted") in (True,1,"true","1"):
                counts["restricted"]+=1;continue
            idv=str(item.get("File","")).strip()
            stream=str(item.get("URL","")).strip()
            if not idv: counts["file_empty"]+=1;continue
            if not stream:counts["stream_empty"]+=1;continue
            parsed=urllib.parse.urlsplit(stream)
            if parsed.scheme.lower()!="https": counts["invalid_https"]+=1;continue
            host=(parsed.hostname or "").lower()
            if host!="my.mail.ru" and not host.endswith(".my.mail.ru"):
                counts["invalid_host"]+=1;continue
            name=(item.get("Name") or item.get("Name_Text_HTML") or "")
            if not str(name).strip():counts["name_empty"]+=1;continue
            counts["accepted"]+=1
        sample=items[0] if items else {}
        print("PARSER_CHECK",repr(term),counts,
              "first_file_type",type(sample.get("File")).__name__,
              "first_url_type",type(sample.get("URL")).__name__,
              "first_url_host",urllib.parse.urlsplit(str(sample.get("URL",""))).hostname,
              "first_name_present",bool(sample.get("Name") or sample.get("Name_Text_HTML")),
              "first_restriction_type",type(sample.get("IsRestricted")).__name__)
    except Exception as e:
        print("PARSER_CHECK",repr(term),"ERROR",str(e)[:160])
