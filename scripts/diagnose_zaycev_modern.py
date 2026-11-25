import re,json,urllib.request,urllib.parse
UA="Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36"
def get(url,cap=1000000):
 try:
  req=urllib.request.Request(url,headers={"User-Agent":UA,"Accept":"*/*","Referer":"https://zaycev.net/","Accept-Language":"ru-RU,ru;q=0.9"})
  with urllib.request.urlopen(req,timeout=12) as response:
   body=response.read(cap).decode("utf-8","replace")
   return body
 except Exception as e:
  print("FETCH_FAIL",url[:110],str(e)[:70])
  return ""
html=get("https://zaycev.net/pages/250772/25077263.shtml")
search=get("https://zaycev.net/search.html?query_search=%D0%9A%D0%B8%D0%BD%D0%BE")
for label,doc in [("SONG",html),("SEARCH",search)]:
 m=re.search(r'<script[^>]*id="__NEXT_DATA__"[^>]*>([\s\S]*?)</script>',doc)
 if not m:
  print(label,"NO_NEXT_DATA");continue
 parsed=json.loads(m.group(1))
 conf=parsed.get("runtimeConfig",{})
 print("CONFIG_KEYS",label,sorted(conf.keys()))
 for k,v in conf.items():
  if ("api" in k.lower() or "player" in k.lower() or "stream" in k.lower() or "url" in k.lower()):
   val=str(v)
   if len(val)<140 and not any(z in k.lower() for z in ["key","token","secret","password"]):
    print("CONFIG_PUBLIC",k,val)
 print("PAGE",label,parsed.get("page"))
 props=parsed.get("props",{}).get("pageProps",{})
 print("PROPS",label,{k:str(v)[:180] for k,v in props.items() if len(str(v))<200})
 if "trackSettings" in props:print("TRACK_SETTINGS",props["trackSettings"])
# Test old API hello body and check if public API offers missing auth request token
hello=get("https://api.zaycev.net/external/hello",300)
print("PUBLIC_HELLO",hello[:220])
scripts=re.findall(r'<script[^>]+src=["\x27]([^"\x27]+)',html)
api_re=re.compile(r'https?://[a-zA-Z0-9./?_\-%]+')
hints=["api.zaycev","muza-api","track/play","playTrack","audioUrl","streamUrl","stream_url","trackUrl","getTrack","getAudio","getPlay","downloadUrl","download_url","getDownload","/external/","/v1/tracks","/api/tracks","/api/"]
for idx,path in enumerate(scripts):
 if not (path.startswith("/") or "zaycev.net" in path):continue
 url=urllib.parse.urljoin("https://zaycev.net/",path)
 code=get(url,950000)
 if not code:continue
 hits={v:code.lower().count(v.lower()) for v in hints if v.lower() in code.lower()}
 if not hits: continue
 print("JS",idx,url[-80:],"CHARS",len(code),"HITS",hits)
 for term in hints:
  match=re.search(re.escape(term),code,re.I)
  if match and term not in ("getTrack","getAudio"):
   prefix=code[max(0,match.start()-135):match.start()+200]
   # Never print API credentials or full signed media URLs
   prefix=re.sub(r'(access_token|authorization|token|api_key)=[^&" ]+',r'\1=[REDACTED]',prefix,flags=re.I)
   print("  CONTEXT",term,repr(prefix[:310]))
