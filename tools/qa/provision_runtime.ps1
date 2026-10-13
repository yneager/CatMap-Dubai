$ErrorActionPreference='Stop'
$taskAuth='http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1'
$taskDb='http://127.0.0.1:8080/v1/projects/demo-catmap/databases/(default)/documents'
$taskSuffix=[guid]::NewGuid().ToString('N').Substring(0,8)
$taskEmail='qa-'+$taskSuffix+'@catmap.test'
$taskPassword=[guid]::NewGuid().ToString('N')
$taskCreated=Invoke-RestMethod -Method Post -Uri ($taskAuth+'/accounts:signUp?key=demo-api-key') -ContentType 'application/json' -Body (@{email=$taskEmail;password=$taskPassword;returnSecureToken=$true}|ConvertTo-Json)
$taskUid=$taskCreated.localId
$null=Invoke-RestMethod -Method Post -Uri ($taskAuth+'/accounts:update?key=demo-api-key') -Headers @{Authorization='Bearer owner'} -ContentType 'application/json' -Body (@{localId=$taskUid;emailVerified=$true}|ConvertTo-Json)
$taskUsername='qatester_'+$taskSuffix
$taskFields=@{
 username=@{stringValue=$taskUsername};displayName=@{stringValue='QA Tester'};role=@{stringValue='admin'}
 suspended=@{booleanValue=$false};volunteerStatus=@{stringValue='none'};points=@{integerValue='0'}
 createdAt=@{integerValue=([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds().ToString())}
}
$null=Invoke-RestMethod -Method Patch -Uri ($taskDb+'/users/'+$taskUid) -Headers @{Authorization='Bearer owner'} -ContentType 'application/json' -Body (@{fields=$taskFields}|ConvertTo-Json -Depth 6)
$null=Invoke-RestMethod -Method Patch -Uri ($taskDb+'/usernames/'+$taskUsername) -Headers @{Authorization='Bearer owner'} -ContentType 'application/json' -Body (@{fields=@{uid=@{stringValue=$taskUid};loginEmail=@{stringValue=$taskEmail}}}|ConvertTo-Json -Depth 6)
$null=Invoke-RestMethod -Method Patch -Uri ($taskDb+'/users/'+$taskUid+'/private/account') -Headers @{Authorization='Bearer owner'} -ContentType 'application/json' -Body (@{fields=@{email=@{stringValue=$taskEmail}}}|ConvertTo-Json -Depth 6)
# Windows encrypts this credential for the current OS account; no existing credentials are read.
New-Item -ItemType Directory -Path build/prompt-shots -Force | Out-Null
$taskSecure=ConvertTo-SecureString -String $taskPassword -AsPlainText -Force
[pscredential]::new($taskEmail,$taskSecure)|Export-Clixml -LiteralPath build/prompt-runtime-credential.local.xml
@{uid=$taskUid;username=$taskUsername;email=$taskEmail}|ConvertTo-Json|Set-Content -LiteralPath build/prompt-runtime-user.json
$taskAdb='C:\Users\mohammad\AppData\Local\Android\Sdk\platform-tools\adb.exe'
# Coordinates came from the current login UI. Do not dump a filled password field.
& $taskAdb -s emulator-5554 shell input tap 540 805
& $taskAdb -s emulator-5554 shell input text $taskEmail
& $taskAdb -s emulator-5554 shell input keyevent 4
& $taskAdb -s emulator-5554 shell input tap 540 1010
& $taskAdb -s emulator-5554 shell input text $taskPassword
& $taskAdb -s emulator-5554 shell input keyevent 4
& $taskAdb -s emulator-5554 shell input tap 540 1185
Write-Output 'Provisioned a disposable local QA administrator and submitted login; verify the screen before recording success.'
