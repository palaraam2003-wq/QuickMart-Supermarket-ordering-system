# Run as Administrator. Enables mixed-mode (SQL + Windows) authentication and TCP/IP on port 1433
# for SQL Server Express (instance SQLEXPRESS), restarts it, then runs create-database.sql.
$ErrorActionPreference = 'Stop'
$root = 'HKLM:\SOFTWARE\Microsoft\Microsoft SQL Server\MSSQL17.SQLEXPRESS\MSSQLServer'
Set-ItemProperty $root -Name LoginMode -Value 2
$tcp = "$root\SuperSocketNetLib\Tcp"
Set-ItemProperty $tcp -Name Enabled -Value 1
Set-ItemProperty "$tcp\IPAll" -Name TcpPort -Value '1433'
Set-ItemProperty "$tcp\IPAll" -Name TcpDynamicPorts -Value ''
Restart-Service 'MSSQL$SQLEXPRESS' -Force
Start-Sleep 5
sqlcmd -S '.\SQLEXPRESS' -E -C -b -i "$PSScriptRoot\create-database.sql"
Write-Host 'Done. Port 1433 open, mixed mode on, databases created.'
