#!/bin/bash
export MSYS_NO_PATHCONV=1
# Tiny adb driver: shot NAME | ui | tap X Y | text STR | key CODE | find TEXT
ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
S="$(cd "$(dirname "$0")/.." && pwd)/build"; mkdir -p "$S/shots"
case "$1" in
  shot) "$ADB" exec-out screencap -p > "$S/shots/$2.png"; echo "$S/shots/$2.png" ;;
  ui) "$ADB" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; "$ADB" exec-out cat /sdcard/ui.xml | python -c "
import sys,re
x=sys.stdin.read()
for m in re.finditer(r'<node [^>]*>',x):
    n=m.group(0); t=re.search(r'text=\"([^\"]*)\"',n).group(1); d=re.search(r'content-desc=\"([^\"]*)\"',n).group(1); b=re.search(r'bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"',n)
    c=re.search(r'clickable=\"(\w+)\"',n).group(1); e=re.search(r'class=\"([^\"]*)\"',n).group(1).split('.')[-1]
    if t or d or e=='EditText':
        x1,y1,x2,y2=map(int,b.groups()); print(f'{(x1+x2)//2},{(y1+y2)//2}\t{e}\t{\"C\" if c==\"true\" else \" \"}\t{t!r} {d!r}')
" ;;
  tap) "$ADB" shell input tap "$2" "$3" ;;
  text) "$ADB" shell input text "$2" ;;
  key) "$ADB" shell input keyevent "$2" ;;
  swipe) "$ADB" shell input swipe "$2" "$3" "$4" "$5" "${6:-300}" ;;
esac
