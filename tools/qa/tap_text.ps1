param([Parameter(Mandatory)][string]$Text)
$ErrorActionPreference='Stop'
$taskAdb='C:\Users\mohammad\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$null=& $taskAdb -s emulator-5554 shell uiautomator dump /sdcard/prompt-tap.xml
$null=& $taskAdb -s emulator-5554 pull /sdcard/prompt-tap.xml build/prompt-shots/tap-ui.xml
[xml]$taskUi=Get-Content build/prompt-shots/tap-ui.xml
$taskNode=@($taskUi.SelectNodes('//node'))|Where-Object {($_.text -eq $Text -or $_.GetAttribute('content-desc') -eq $Text) -and $_.password -ne 'true'}|Select-Object -First 1
if(!$taskNode) {throw "Text not visible: $Text"}
$taskBounds=[regex]::Match($taskNode.bounds,'\[(\d+),(\d+)\]\[(\d+),(\d+)\]')
$taskX=[int]( ([int]$taskBounds.Groups[1].Value+[int]$taskBounds.Groups[3].Value)/2 )
$taskY=[int]( ([int]$taskBounds.Groups[2].Value+[int]$taskBounds.Groups[4].Value)/2 )
& $taskAdb -s emulator-5554 shell input tap $taskX $taskY
Write-Output ('Tapped visible control: '+$Text)
