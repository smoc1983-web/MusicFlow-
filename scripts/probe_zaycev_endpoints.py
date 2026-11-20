import urllib.request,urllib.parse,re,json
UA="Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36"
def get(url,limit=900000):
 try:
  req=urllib.request.Request(url,headers={"User-Agent":UA,"Referer":"https://zaycev.net/","Accept":"application/json, */*","Accept-Language":"ru-RU,ru;q=0.9"})
  with urllib.request.urlopen(req,timeout=11) as r:
   body=r.read(limit)
   print("URL_STATUS",r.status,urllib.parse.urlsplit(r.url).path[:110],r.headers.get("Content-Type"),len(body))
   return body.decode("utf-8","replace")
 except Exception as e:
  print("URL_ERROR",url[:130],str(e)[:100])
  return ""
url="https://zaycev.net/_next/static/chunks/pages/_app-a244bb2911f750815e0d.js"
js=get(url)
for phrase in ["getTrackPlayInfoByHash","getTrackDownloadUrl","getTracksInfo","getTrackInfo","getTrackById","track/tracks/info","api.create","axios.create","baseURL:","this.api=","localApiUrl","serverApiUrl","getPlayInfo","track/play/"]:
 matches=list(re.finditer(re.escape(phrase),js,re.I))
 print("JS_MATCH",phrase,len(matches))
 for match in matches[:3]:
  snippet=js[max(0,match.start()-300):match.end()+360]
  # Public bundled JavaScript only; output exact route fragments, redact auth hints
  snippet=re.sub(r'(access_token|secret|api_key)[\w=:" ]{0,80}',"[AUTH_REDACTED]",snippet,flags=re.I)
  print("JS_CONTEXT",phrase,repr(snippet[:650]))
search=get("https://zaycev.net/search.html?query_search=%D0%9A%D0%B8%D0%BD%D0%BE")
nextmatch=re.search(r'<script[^>]*id="__NEXT_DATA__"[^>]*>([\s\S]*?)</script>',search)
if nextmatch:
 data=json.loads(nextmatch.group(1))
 print("SEARCH_PROPS",list(data.get("props",{}).get("pageProps",{}).keys()))
 print("QUERY",data.get("query"))
# Anonymous endpoint checks, no emulated credentials.
for url in [
 "https://zaycev.net/track/tracks/info",
 "https://zaycev.net/track/play/25077263",
 "https://zaycev.net/api/track/play/25077263",
 "https://zaycev.net/api/v1/track/play/25077263",
 "https://api.zaycev.net/track/play/25077263",
 "https://zaycev.net/track/25077263",
 "https://api.zaycev.net/external/hello"]:
 body=get(url,320)
 if body: print("PUBLIC_REPLY_KEYS",url,repr(body[:130]))
hello=get("https://api.zaycev.net/external/hello",220)
try:
 anonymous=json.loads(hello).get("token","")
 if anonymous and len(anonymous)<110:
  for path in ["/external/search?query=%D0%9A%D0%B8%D0%BD%D0%BE&page=1&type=all&sort=popularity&style=&active_subscribe=0",
  "/external/track/25077263"]:
   sep="&" if "?" in path else "?"
   data=get("https://api.zaycev.net"+path+sep+"access_token="+urllib.parse.quote(anonymous),360)
   print("PUBLIC_HELLO_TOKEN_REQUEST",path.split("?")[0],repr(data[:150]))
except Exception as e: print("HELLO_PARSE_ERROR",type(e).__name__)

print("---- CURRENT PUBLIC /api/external PROXY ----")
for path in [
 "/api/external/track/play/25077263",
 "/api/external/track/tracks/info",
 "/api/external/track/25077263",
 "/api/external/track/download/25077263",
 "/api/external/pages/search?query_search=%D0%9A%D0%B8%D0%BD%D0%BE",
 "/api/external/pages/search?query=%D0%9A%D0%B8%D0%BD%D0%BE",
 "/api/external/search?query=%D0%9A%D0%B8%D0%BD%D0%BE"
]:
 b=get("https://zaycev.net"+path,600)
 if b:print("PROXY_GET",path,repr(b[:340]))
import json
for path,payload in [
 ("/api/external/track/tracks/info",{"tracksList":["25077263"],"source":"search"}),
 ("/api/external/track/tracks/info",{"tracksList":[25077263],"source":"search"}),
 ("/api/external/track/filezmeta",{"trackIds":[25077263],"subscription":False}),
]:
 address="https://zaycev.net"+path
 req=urllib.request.Request(address,data=json.dumps(payload).encode(),headers={
 "User-Agent":UA,"Referer":"https://zaycev.net/pages/250772/25077263.shtml",
 "Accept":"application/json","Content-Type":"application/json",
 "Origin":"https://zaycev.net","Accept-Language":"ru-RU,ru;q=0.9"},method="POST")
 try:
  with urllib.request.urlopen(req,timeout=13) as resp:
   body=resp.read(1200).decode("utf-8","replace")
   print("PROXY_POST",path,"STATUS",resp.status,"TEXT",repr(body[:850]))
 except Exception as e: print("PROXY_POST_ERROR",path,str(e)[:150])
# Find search + track-data route names in public Next bundle.
for key in ["getSearchResults","getSearch","pages/search","search/query","getTrack","getTrackInfo","getSingleTrack","pages/track","getTrackPlayInfoByHash"]:
 for m in list(re.finditer(re.escape(key),js,re.I))[-2:]:
  print("API_JS_ROUTE",key,repr(js[max(0,m.start()-170):m.end()+220][:410]))

print("---- VERIFIED ANONYMOUS MEDIA RESOLUTION ----")
def post(path, obj):
 req=urllib.request.Request(
 "https://zaycev.net/api/external/"+path,
 data=json.dumps(obj).encode(),method="POST",headers={
 "User-Agent":UA,"Referer":"https://zaycev.net/",
 "Accept":"application/json","Content-Type":"application/json",
 "Origin":"https://zaycev.net"})
 try:
  with urllib.request.urlopen(req,timeout=13) as response:
   result=json.load(response)
   print("POST_KEYS",path,list(result) if isinstance(result,dict) else type(result).__name__)
   return result
 except Exception as e:
  print("POST_ERROR",path,str(e)[:110])
  return {}
meta=post("track/filezmeta",{"trackIds":[25077263],"subscription":False})
for item in meta.get("tracks",[])[:2]:
 hash=item.get("streaming")
 if hash:
  import urllib.parse
  raw=get("https://zaycev.net/api/external/track/play/"+str(hash),600)
  if raw:
   try:
    obj=json.loads(raw)
    print("PLAY_KEYS",list(obj) if isinstance(obj,dict) else type(obj).__name__)
    stream=obj.get("url","")
    if stream:
     uri=urllib.parse.urlsplit(stream)
     print("PLAY_LINK",uri.scheme,uri.netloc,uri.path.rsplit(".",1)[-1], "LEN",len(stream))
     req=urllib.request.Request(stream,headers={
      "User-Agent":UA,"Range":"bytes=0-1","Referer":"https://zaycev.net/"})
     try:
      with urllib.request.urlopen(req,timeout=14) as f:
       print("AUDIO_PROBE",f.status,f.headers.get("Content-Type"),"SAMPLE",len(f.read(2)))
     except Exception as e:print("AUDIO_PROBE_FAIL",str(e)[:140])
   except Exception as e: print("PLAY_PARSE_ERROR",str(e)[:120])
for term in ("Кино", "Teddy Swims"):
 endpoint="https://zaycev.net/api/external/pages/search/tracks?q="+urllib.parse.quote(term)+"&page=1&limit=10"
 raw=get(endpoint,2200)
 if raw:
  try:
   obj=json.loads(raw)
   print("SEARCH_PUBLIC",term,"ROOT_KEYS",list(obj),"LEN",len(raw))
   for k,v in obj.items():
    print("SEARCH_NODE",k,type(v).__name__, len(v) if isinstance(v,(list,dict)) else "", "SAMPLE_KEYS",
      list(v[0].keys())[:18] if isinstance(v,list) and v and isinstance(v[0],dict) else list(v)[:12] if isinstance(v,dict) else [])
  except Exception as e:print("SEARCH_PARSE_FAIL",term,str(e)[:120])

print("---- FULL PUBLIC SEARCH JSON SCHEMA ----")
for term in ("Кино", "Teddy Swims"):
 path="/api/external/pages/search/tracks?q="+urllib.parse.quote(term)+"&page=1&limit=15"
 raw=get("https://zaycev.net"+path,130000)
 if raw:
  try:
   data=json.loads(raw)
   print("SEARCH_SCHEMA",term,"root",list(data) if isinstance(data,dict) else type(data).__name__,
     "jsonLen",len(raw))
   if isinstance(data,dict):
    for k,v in data.items():
     print("FIELD",k,"type",type(v).__name__,"count",len(v) if isinstance(v,(list,dict)) else "")
     if isinstance(v,list) and v:
      print("LIST_ITEM",k,"firstType",type(v[0]).__name__,
       "keys",list(v[0])[:30] if isinstance(v[0],dict) else "scalar",
       "firstScalar",str(v[0])[:20] if isinstance(v[0],int) else "")
     if isinstance(v,dict):
      print("DICT_KEYS",k,list(v)[:20])
      if v:
       obj=next(iter(v.values()))
       print("DICT_SAMPLE_KEYS",k,list(obj)[:28] if isinstance(obj,dict) else type(obj).__name__)
  except Exception as e:print("SEARCH_JSON_ERROR",term,str(e)[:150])
