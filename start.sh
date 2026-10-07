#!/usr/bin/env bash
# LOKTV Hook Pro — static preview server (dist/index.html + APK).
# Serves the built static directory in the foreground on $PORT (default 3000)
# and publishes deployment-output.json for the controller.
set -euo pipefail
time -p cd "$(dirname "$0")"
time -p pwd
PROJECT_DIR="$PWD"
time -p test -f "$PROJECT_DIR/dist/index.html"
PORT="${PORT:-3000}"
export PORT
DIST="$PROJECT_DIR/dist"
WEB_DIR="${OPENCODE_WEB_DIR:-/home/runner/work/_temp/omgithub-web}"
time -p mkdir -p "$WEB_DIR"
OUT="$WEB_DIR/deployment-output.json" PROJ="$PROJECT_DIR" DIR="$DIST" time -p node -e '
const fs=require("fs");
fs.writeFileSync(process.env.OUT, JSON.stringify({project: process.env.PROJ, directory: process.env.DIR}));
console.log("wrote " + process.env.OUT);
'
echo "serving $DIST on port $PORT (project $PROJECT_DIR)"
# Foreground static server (no dependencies): index.html + apk + assets, no-cache html.
DIST="$DIST" PORT="$PORT" node -e '
const http=require("http"),fs=require("fs"),path=require("path");
const root=process.env.DIST, port=Number(process.env.PORT||3000);
const mime={".html":"text/html;charset=utf-8",".css":"text/css",".js":"application/javascript",".json":"application/json",".png":"image/png",".jpg":"image/jpeg",".apk":"application/vnd.android.package-archive",".idsig":"application/octet-stream",".svg":"image/svg+xml"};
http.createServer((req,res)=>{
try{
const u=new URL(req.url,"http://localhost");
let p=path.normalize(path.join(root,decodeURIComponent(u.pathname)));
if(!p.startsWith(root)){res.writeHead(404);res.end();return;}
if(fs.statSync(p).isDirectory())p=path.join(p,"index.html");
res.setHeader("Content-Type",mime[path.extname(p)]||"application/octet-stream");
res.setHeader("Cache-Control","no-cache");
fs.createReadStream(p).pipe(res);
}catch(e){res.writeHead(404);res.end("Not found");}
}).listen(port,"0.0.0.0",()=>console.log("listening on "+port+" serving "+root));
'
