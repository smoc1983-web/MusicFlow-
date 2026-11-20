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
