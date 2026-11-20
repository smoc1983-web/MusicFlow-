import urllib.request,urllib.error,urllib.parse,re,json,html
UA="Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36"
def get(url,limit=1400000):
  req=urllib.request.Request(url,headers={"User-Agent":UA,"Accept-Language":"ru-RU,ru;q=0.9","Referer":"https://zaycev.net/","Accept":"*/*"})
  try:
    with urllib.request.urlopen(req,timeout=13) as f:
      buf=f.read(limit)
      print("URL",url[:130],"STATUS",f.status,"MIME",f.headers.get("Content-Type"),"LEN",len(buf),"FINAL_HOST",urllib.parse.urlsplit(f.url).hostname)
      return buf.decode("utf-8","replace")
  except Exception as e:
    print("URL",url[:130],"ERROR",str(e)[:110])
    return ""
home=get("https://zaycev.net/")
song=get("https://zaycev.net/pages/250772/25077263.shtml")
search=get("https://zaycev.net/search.html?query_search=%D0%9A%D0%B8%D0%BD%D0%BE")
search2=get("https://zaycev.net/search.html?query=%D0%9A%D0%B8%D0%BD%D0%BE")
search3=get("https://zaycev.net/search?query=%D0%9A%D0%B8%D0%BD%D0%BE")
endpoints=[
 "https://api.zaycev.net/external/search?query=%D0%9A%D0%B8%D0%BD%D0%BE&page=1&type=all&sort=popularity&style=&active_subscribe=0",
 "https://api.zaycev.net/external/track/25077263/play?active_subscribe=0",
 "https://api.zaycev.net/external/track/25077263?active_subscribe=0",
 "https://zaycev.net/api/track/25077263",
 "https://zaycev.net/api/track/25077263/play",
 "https://zaycev.net/api/search?query=%D0%9A%D0%B8%D0%BD%D0%BE"
]
for url in endpoints:
  data=get(url,1800)
  if data:
    print("RESPONSE_PREVIEW",data[:170].replace("\n"," "))
for label,blob in [("HOME",home),("SONG",song),("SEARCH",search),("SEARCH2",search2),("SEARCH3",search3)]:
  print("HTML_MARKERS",label,{s:blob.lower().count(s.lower()) for s in ["zaycev","__NEXT_DATA__","__NUXT__","audio/","mp3","m3u8","play","track","search"]})
  srcs=re.findall(r'<script[^>]+src=["\x27]([^"\x27]+)',blob,re.I)
  print("SCRIPTS",label,len(srcs),srcs[:16])
  for pattern in [r'<form[^>]*action=["\x27]([^"\x27]+)',r'<input[^>]*name=["\x27]([^"\x27]+)',r'<a[^>]+href=["\x27]([^"\x27]*search[^"\x27]*)']:
    print("FORMS",label,re.findall(pattern,blob,re.I)[:8])
  if label=="SONG":
    for key in ["download","audio_url","fileUrl","playUrl","stream","api.","data-url","mp3","currentUrl"]:
      pos=blob.lower().find(key.lower())
      if pos>=0:print("SONG_CTX",key,repr(blob[max(0,pos-130):pos+190]))
# Try bundles from existing pages (limited), inspect API endpoint markers
scripts=re.findall(r'<script[^>]+src=["\x27]([^"\x27]+)',home,re.I)
for path in scripts[-8:]:
  url=urllib.parse.urljoin("https://zaycev.net/",path)
  code=get(url,240000)
  print("JS_HINTS",url.split("/")[-1][:35],{key:code.lower().count(key) for key in ["api.zaycev","track/play","playtrack","/api/","/search","mp3","files/","download","getstream","audio"]})
  for key in ["api.zaycev","track/play","/api/track","/api/music","mp3"]:
    pos=code.lower().find(key.lower())
    if pos>=0: print("JS_CONTEXT",key,code[max(0,pos-160):pos+180].replace("\n"," ")[:320])
