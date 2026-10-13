param([string]$Route='map',[string]$Name='map')
$ErrorActionPreference='Stop'
$taskAdb='C:\Users\mohammad\AppData\Local\Android\Sdk\platform-tools\adb.exe'
if($Name -notmatch '^[a-zA-Z0-9_-]+$') {throw 'Unsafe screenshot name'}
$null=& $taskAdb -s emulator-5554 shell am start -W -f 0x14000000 -n com.catmap.dubai.staging/com.catmap.dubai.MainActivity --es route $Route
$null=& $taskAdb -s emulator-5554 shell uiautomator dump /sdcard/prompt-capture.xml
$null=& $taskAdb -s emulator-5554 pull /sdcard/prompt-capture.xml "build/prompt-shots/$Name.xml"
$null=& $taskAdb -s emulator-5554 shell screencap -p /sdcard/prompt-capture.png
$null=& $taskAdb -s emulator-5554 pull /sdcard/prompt-capture.png "build/prompt-shots/$Name.png"
[xml]$taskUi=Get-Content -LiteralPath "build/prompt-shots/$Name.xml"
$labels=@($taskUi.SelectNodes('//node[@text!="" and @password!="true"]')|ForEach-Object {$_.text})
@{route=$Route;labels=$labels}|ConvertTo-Json -Depth 3|Set-Content -LiteralPath "build/prompt-shots/$Name-labels.json"
Write-Output ($Name+': '+(($labels|Select-Object -First 4)-join ' | '))
