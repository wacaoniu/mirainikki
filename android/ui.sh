#!/usr/bin/env bash
# 模拟器端到端测试驱动：uiautomator dump + input tap
# 用法: source ui.sh <serial>
SER="${1:-emulator-5554}"
ADB="F:\\tool\\AndroidSDK\\platform-tools\\adb.exe"

dump() {
  "$ADB" -s "$SER" exec-out uiautomator dump /dev/tty 2>/dev/null | node -e "
let s='';process.stdin.on('data',d=>s+=d).on('end',()=>{
  const m=s.match(/<hierarchy.*<\/hierarchy>/s); if(!m){console.log('NO_TREE');return;}
  const xml=m[0].replace(/&#(\d+);/g,(_,n)=>String.fromCodePoint(+n));
  const re=/<node[^>]*text=\"([^\"]*)\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"[^>]*>/g;
  let mm;
  while((mm=re.exec(xml))){
    const t=mm[1]; if(!t) continue;
    console.log(t+' | '+Math.round((+mm[2]+ +mm[4])/2)+' '+Math.round((+mm[3]+ +mm[5])/2));
  }
});"
}

# tap_text <精确文本> —— 在当前界面按文本找控件并点击
tap_text() {
  local target="$1"
  local coords
  coords=$("$ADB" -s "$SER" exec-out uiautomator dump /dev/tty 2>/dev/null | node -e "
let s='';process.stdin.on('data',d=>s+=d).on('end',()=>{
  const m=s.match(/<hierarchy.*<\/hierarchy>/s); if(!m)process.exit(1);
  const xml=m[0].replace(/&#(\d+);/g,(_,n)=>String.fromCodePoint(+n));
  const re=/<node[^>]*text=\"([^\"]*)\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"[^>]*>/g;
  let mm;
  while((mm=re.exec(xml))){
    if(mm[1]===process.argv[1]){
      console.log(Math.round((+mm[2]+ +mm[4])/2)+' '+Math.round((+mm[3]+ +mm[5])/2));
      process.exit(0);
    }
  }
  process.exit(2);
}" "$target" 2>/dev/null)
  if [ -z "$coords" ]; then echo "NOT_FOUND: $target"; return 1; fi
  "$ADB" -s "$SER" shell input tap $coords
  sleep 1
  echo "TAPPED: $target @ $coords"
}

tap() { "$ADB" -s "$SER" shell input tap $1 $2; sleep "${3:-1}"; }
type_text() { "$ADB" -s "$SER" shell input text "$1"; sleep 0.5; }
back() { "$ADB" -s "$SER" shell input keyevent KEYCODE_BACK; sleep 0.8; }
hide_kb() { "$ADB" -s "$SER" shell input keyevent KEYCODE_ESCAPE; sleep 0.5; }
