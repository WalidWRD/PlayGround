#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
PROJECT_ROOT="$(pwd)"
DIST="$PROJECT_ROOT/dist"
PORT="${PORT:-3000}"
export PORT
OPENCODE_WEB_DIR="${OPENCODE_WEB_DIR:-/home/runner/work/_temp/omgithub-web}"
export DIST_DIR="$DIST"
/usr/bin/time -p mkdir -p "$DIST" "$OPENCODE_WEB_DIR"
if [ -f "$PROJECT_ROOT/package.json" ]; then
  /usr/bin/time -p npm --version
  if [ -f "$PROJECT_ROOT/package-lock.json" ]; then /usr/bin/time -p npm ci --no-audit --no-fund; else /usr/bin/time -p npm install --no-audit --no-fund; fi
  if [ -f "$DIST/index.html" ]; then echo "dist exists, skip build"; else /usr/bin/time -p npm run build; fi
fi
/usr/bin/time -p test -f "$DIST/index.html"
/usr/bin/time -p /usr/bin/printf '%s' "{\"project\":\"$PROJECT_ROOT\",\"directory\":\"$DIST\"}" > "$OPENCODE_WEB_DIR/deployment-output.json"
/usr/bin/time -p cat "$OPENCODE_WEB_DIR/deployment-output.json"
/usr/bin/time -p node --version
exec /usr/bin/time -p node -e '
const {createServer}=require("node:http");
const {readFileSync,statSync,existsSync}=require("node:fs");
const {resolve,join,extname}=require("node:path");
const root=process.env.DIST_DIR||resolve(process.cwd(),"dist");
const port=Number(process.env.PORT||3000);
const mime={".html":"text/html",".js":"application/javascript",".css":"text/css",".json":"application/json",".svg":"image/svg+xml",".png":"image/png",".jpg":"image/jpeg",".webp":"image/webp",".wasm":"application/wasm",".glb":"model/gltf-binary"};
if(!existsSync(join(root,"index.html"))){console.error("missing dist/index.html");process.exit(1);}
createServer((req,res)=>{
try{
const u=new URL(req.url,"http://localhost");
let p=resolve(root,"."+decodeURIComponent(u.pathname));
if(p!==resolve(root)&&!p.startsWith(resolve(root)+"/")){res.writeHead(404);res.end();return;}
try{if(statSync(p).isDirectory())p=join(p,"index.html");}catch{p=join(root,"index.html");}
res.setHeader("Content-Type",mime[extname(p)]||"application/octet-stream");
res.setHeader("Cache-Control","no-cache");
res.end(readFileSync(p));
}catch(e){res.writeHead(404);res.end("Not found");}
}).listen(port,"0.0.0.0",()=>console.log("serving "+root+" on "+port));
'
