param([ValidateSet('save','load')][string]$Mode,[string]$Path)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Security
if($Mode -eq 'save') {
    $plain=[Text.Encoding]::UTF8.GetBytes([Console]::In.ReadToEnd())
    $sealed=[Security.Cryptography.ProtectedData]::Protect($plain,$null,[Security.Cryptography.DataProtectionScope]::CurrentUser)
    [IO.File]::WriteAllBytes($Path+'.tmp',$sealed)
    Move-Item -LiteralPath ($Path+'.tmp') -Destination $Path -Force
} elseif(Test-Path -LiteralPath $Path) {
    $plain=[Security.Cryptography.ProtectedData]::Unprotect([IO.File]::ReadAllBytes($Path),$null,[Security.Cryptography.DataProtectionScope]::CurrentUser)
    [Console]::Out.Write([Text.Encoding]::UTF8.GetString($plain))
}
