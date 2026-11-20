import json,re,urllib.parse,urllib.request,urllib.error
UA="Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36"
def grab(name,link):
    req=urllib.request.Request(link,headers={"User-Agent":UA,"Accept-Language":"ru-RU,ru;q=0.9","Accept":"text/html,application/json,*/*"})
    try:
        with urllib.request.urlopen(req,timeout=15) as response:
            data=response.read(1_500_000).decode("utf-8","replace")
            print(name,"HTTP",response.status,"FINAL",response.url,"CONTENT",response.headers.get("Content-Type"),"LEN",len(data))
            for marker in ["audio/mpeg",".mp3","metafilez","playUrl","streamUrl","downloadUrl","track-id","data-id","data-url","__NEXT_DATA__","__INITIAL_STATE__","api.zaycev","musicset-track","application/ld+json","ajax","player"]:
                print("MARK",name,marker,data.lower().count(marker.lower()))
            for pat in [r'<script[^>]+src="([^"]+)"',r'<a[^>]+href="(/pages/[^"]+)"',r'<(?:div|li|button)[^>]+(?:data-id|data-track-id|data-url)="[^"]+"[^>]*>',r'<(?:source|audio)[^>]+(?:src|data-src)="[^"]+"']:
                hits=re.findall(pat,data,re.I)
                print("REGEX",name,pat[:22],len(hits),[(x[:160] if isinstance(x,str) else str(x)[:160]) for x in hits[:4]])
            return data
    except Exception as e:
        print(name,"ERROR",type(e).__name__,str(e)[:100])
        return ""
home=grab("HOME","https://zaycev.net/")
top=grab("TOP","https://zaycev.net/popular/index.html")
song=grab("SONG","https://zaycev.net/pages/250772/25077263.shtml")
search=grab("SEARCH1","https://zaycev.net/search.html?query=%D0%9A%D0%B8%D0%BD%D0%BE")
search2=grab("SEARCH2","https://zaycev.net/search?query=%D0%9A%D0%B8%D0%BD%D0%BE")
api=grab("OLD_API","https://api.zaycev.net/external/hello")
it=grab("ART","https://itunes.apple.com/search?term=Stromae%20Alors%20on%20Danse&media=music&entity=song&limit=3")
if it:
    try:
        obj=json.loads(it)
        print("ART_COUNT",obj.get("resultCount",0),"FIRST_KEYS",list(obj.get("results",[{}])[0].keys())[:25])
    except Exception as e:print("ART_PARSE_ERROR",repr(e))
