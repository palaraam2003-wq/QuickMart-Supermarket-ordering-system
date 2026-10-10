# Credentials are inherited by the background supervisor; never written to disk.
# Use your mail provider's SMTP credentials/app password, not a customer password.
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$smtpHost = 'smtp.gmail.com'
$smtpPort = '587'
$smtpUsername = 'palaraam2003@gmail.com'
$smtpFrom = 'palaraam2003@gmail.com'
Write-Host "Sending OTPs and bills using Gmail: $smtpFrom"
if ([string]::IsNullOrWhiteSpace($smtpHost) -or $smtpPort -notin @('587', '465') -or
    [string]::IsNullOrWhiteSpace($smtpUsername) -or $smtpFrom -notmatch '^[^@\s]+@[^@\s]+\.[^@\s]+$') {
    throw 'Enter a host, port 587 or 465, username and valid sender address.'
}
$smtpSecret = Read-Host 'SMTP password or app password (hidden)' -AsSecureString
if ($smtpSecret.Length -eq 0) { throw 'An SMTP password is required.' }
$settings = @{
    RAPIDMART_MAIL_HOST = $smtpHost.Trim()
    RAPIDMART_MAIL_PORT = $smtpPort
    RAPIDMART_MAIL_USERNAME = $smtpUsername.Trim()
    RAPIDMART_MAIL_FROM = $smtpFrom.Trim()
    RAPIDMART_MAIL_SSL = ($smtpPort -eq '465').ToString().ToLowerInvariant()
    RAPIDMART_MAIL_STARTTLS = ($smtpPort -eq '587').ToString().ToLowerInvariant()
    # SMTP downtime must not prevent the ordering/delivery application from starting.
    SPRING_MAIL_TEST_CONNECTION = 'false'
}
$previous = @{}
$keys = @($settings.Keys) + 'RAPIDMART_MAIL_PASSWORD'
foreach ($key in $keys) { $previous[$key] = [Environment]::GetEnvironmentVariable($key, 'Process') }
try {
    foreach ($key in $settings.Keys) { [Environment]::SetEnvironmentVariable($key, $settings[$key], 'Process') }
    $secretPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($smtpSecret)
    try {
        [Environment]::SetEnvironmentVariable('RAPIDMART_MAIL_PASSWORD',
            ([Runtime.InteropServices.Marshal]::PtrToStringBSTR($secretPointer) -replace '\s', ''), 'Process')
    } finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($secretPointer) }
    Write-Host 'Restarting the managed RapidMart server with email settings. SMTP authentication is checked when sending.'
    & (Join-Path $PSScriptRoot 'rapidmart.ps1') -Action Restart -CurrentEnvironment
    Write-Host 'Email settings are active until the supervisor exits. After Windows sign-in, rerun this email launcher or configure persistent mail environment variables.'
} finally {
    foreach ($key in $keys) { [Environment]::SetEnvironmentVariable($key, $previous[$key], 'Process') }
    $smtpSecret.Dispose()
}
