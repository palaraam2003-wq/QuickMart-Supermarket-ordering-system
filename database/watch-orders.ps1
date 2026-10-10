# Live view of customer carts and orders in RapidMartDB. Refreshes every few seconds; press Ctrl+C to stop.
# SSMS result grids are snapshots and never refresh on their own; this shows app changes as they are saved.
# Usage:  powershell -ExecutionPolicy Bypass -File database\watch-orders.ps1 [-Seconds 3] [-Database RapidMartDB]
param(
    [int]$Seconds = 3,
    [string]$Server = "localhost\SQLEXPRESS",
    [string]$Database = "RapidMartDB"
)

$sqlcmd = (Get-Command sqlcmd.exe -ErrorAction SilentlyContinue).Source
if (-not $sqlcmd) { $sqlcmd = "C:\Program Files\Microsoft SQL Server\Client SDK\ODBC\180\Tools\Binn\sqlcmd.exe" }

$query = @"
SET NOCOUNT ON;
PRINT '=== CARTS (items waiting for checkout; a cart is emptied when its order is placed) ===';
SELECT LEFT(u.email, 30) AS customer, p.sku, LEFT(p.name, 22) AS product, ci.quantity,
       FORMAT(ci.updated_at, 'HH:mm:ss') AS changed
FROM cart_items ci
JOIN carts c ON c.id = ci.cart_id
JOIN app_users u ON u.id = c.user_id
JOIN products p ON p.id = ci.product_id
ORDER BY ci.updated_at DESC;
PRINT '';
PRINT '=== LATEST ORDERS (10) ===';
SELECT TOP 10 o.order_number, LEFT(u.email, 30) AS customer, o.status AS order_status,
       (SELECT SUM(i.quantity) FROM order_items i WHERE i.order_id = o.id) AS items,
       o.total_amount, p.payment_method, p.payment_status, d.delivery_status,
       FORMAT(o.created_at, 'yyyy-MM-dd HH:mm:ss') AS placed, FORMAT(o.updated_at, 'HH:mm:ss') AS changed
FROM orders o
JOIN app_users u ON u.id = o.user_id
LEFT JOIN payments p ON p.order_id = o.id
LEFT JOIN deliveries d ON d.order_id = o.id
ORDER BY o.updated_at DESC;
PRINT '';
PRINT '=== LATEST STOCK MOVEMENTS (8) ===';
SELECT TOP 8 FORMAT(h.created_at, 'HH:mm:ss') AS at, p.sku, h.quantity_delta AS delta, h.movement_type, h.reference_id
FROM stock_movement_history h
JOIN products p ON p.id = h.product_id
ORDER BY h.id DESC;
"@

while ($true) {
    $output = & $sqlcmd -S $Server -E -C -d $Database -W -Q $query 2>&1
    Clear-Host
    Write-Host "RapidMart orders & carts - $Database - $(Get-Date -Format 'HH:mm:ss')  (refresh every ${Seconds}s, Ctrl+C to stop)" -ForegroundColor Cyan
    Write-Host ""
    $output | ForEach-Object { "$_" }
    Start-Sleep -Seconds $Seconds
}
