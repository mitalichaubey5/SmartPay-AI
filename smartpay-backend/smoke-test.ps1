# SmartPay AI backend smoke test — exercises every REST endpoint end-to-end.
# Usage: .\smoke-test.ps1  (requires backend running on http://localhost:8080)
$ErrorActionPreference = "Stop"
$base = "http://localhost:8080/api"
$script:passed = 0; $script:failed = 0; $script:token = $null

function Check($name, $scriptBlock) {
    try {
        $result = & $scriptBlock
        Write-Host ("[PASS] {0}" -f $name) -ForegroundColor Green
        if ($result) { Write-Host ("       -> {0}" -f ($result | ConvertTo-Json -Compress -Depth 6)) }
        $script:passed++
    } catch {
        Write-Host ("[FAIL] {0}: {1}" -f $name, $_.Exception.Message) -ForegroundColor Red
        $script:failed++
    }
}

function Api($method, $path, $body = $null, $token = $null) {
    $params = @{ Method = $method; Uri = "$base$path"; ContentType = "application/json" }
    $t = $token; if ($t -eq $null) { $t = $script:token }
    if ($t -ne $null) { $params.Headers = @{ Authorization = "Bearer $t" } }
    if ($body -ne $null) { $params.Body = ($body | ConvertTo-Json -Depth 6) }
    Invoke-RestMethod @params
}

# Returns the HTTP status code for a request without throwing on 4xx/5xx
# (works on both Windows PowerShell 5.1 and PowerShell 7).
function StatusCode($method, $path, $body = $null, $token = $null) {
    try {
        $params = @{ Method = $method; Uri = "$base$path"; ContentType = "application/json"; UseBasicParsing = $true }
        $t = $token; if ($t -eq $null) { $t = $script:token }
        if ($t -ne $null) { $params.Headers = @{ Authorization = "Bearer $t" } }
        if ($body -ne $null) { $params.Body = ($body | ConvertTo-Json -Depth 6) }
        $r = Invoke-WebRequest @params
        return [int]$r.StatusCode
    } catch {
        $resp = $_.Exception.Response
        if ($resp -ne $null) { return [int]$resp.StatusCode }
        throw
    }
}

function Expect($condition, $message) {
    if (-not $condition) { throw $message }
}

# ---------------------------------------------------------------- health
Check "GET /health responds" {
    $h = Api GET "/health"
    Expect ($h.status -eq "UP") "health status not UP"
    Expect ($h.database -eq "UP") "database DOWN"
    "db=$($h.database) invoices=$($h.invoiceCount) ai=$($h.ai.mode) paypal=$($h.paypal.mode)"
}

# ---------------------------------------------------------------- security
Check "GET /customers without a token returns 401" {
    $code = StatusCode GET "/customers"
    Expect ($code -eq 401) "expected 401 got $code"
    "status $code as expected (endpoints are protected)"
}

Check "Invalid token rejected (401)" {
    $code = StatusCode GET "/customers" $null "not.a.real.token"
    Expect ($code -eq 401) "expected 401 got $code"
    "status $code as expected"
}

$script:authEmail = "smoke-$(Get-Date -Format 'yyyyMMddHHmmss')@smartpay.test"
$script:authPassword = "Smoke!Test123"
Check "POST /auth/register returns a token" {
    $res = Api POST "/auth/register" @{ email = $script:authEmail; password = $script:authPassword; fullName = "Smoke Tester" }
    Expect ($res.token) "no token in response"
    Expect ($res.user.email -eq $script:authEmail) "email was $($res.user.email)"
    Expect ($res.user.role -eq "USER") "role was $($res.user.role)"
    $script:token = $res.token
    "user id=$($res.user.id) role=$($res.user.role) tokenExpiresIn=$($res.expiresIn)s"
}

Check "POST /auth/register duplicate email rejected (400)" {
    $code = StatusCode POST "/auth/register" @{ email = $script:authEmail; password = "AnotherPass123"; fullName = "Dup" }
    Expect ($code -eq 400) "expected 400 got $code"
    "status $code as expected"
}

Check "POST /auth/login returns a token" {
    $res = Api POST "/auth/login" @{ email = $script:authEmail; password = $script:authPassword }
    Expect ($res.token) "no token"
    Expect ($res.user.email -eq $script:authEmail) "email mismatch"
    "logged in as $($res.user.email)"
}

Check "POST /auth/login wrong password rejected (400)" {
    $code = StatusCode POST "/auth/login" @{ email = $script:authEmail; password = "WrongPassword123" }
    Expect ($code -eq 400) "expected 400 got $code"
    "status $code as expected"
}

Check "GET /users/me returns the profile" {
    $me = Api GET "/users/me"
    Expect ($me.email -eq $script:authEmail) "email mismatch"
    "me: id=$($me.id) fullName=$($me.fullName) role=$($me.role)"
}

# ---------------------------------------------------------------- AI generation
$script:draft = $null
Check "POST /ai/generate-invoice (local parser)" {
    $prompt = "Invoice Acme Corp for 10 hours of web development at 75/hour, 18% tax, due in 14 days, contact billing@acmecorp.com"
    $script:draft = Api POST "/ai/generate-invoice" @{ prompt = $prompt }
    Expect ($script:draft.clientName -eq "Acme Corp") "clientName was '$($script:draft.clientName)'"
    Expect ($script:draft.clientEmail -eq "billing@acmecorp.com") "email was '$($script:draft.clientEmail)'"
    Expect ($script:draft.taxRate -eq 18) "taxRate was '$($script:draft.taxRate)'"
    Expect ($script:draft.items.Count -ge 1) "no items parsed"
    Expect ($script:draft.items[0].quantity -eq 10) "quantity was '$($script:draft.items[0].quantity)'"
    Expect ($script:draft.items[0].unitPrice -eq 75) "unitPrice was '$($script:draft.items[0].unitPrice)'"
    "source=$($script:draft.source) client=$($script:draft.clientName) tax=$($script:draft.taxRate)% item='$($script:draft.items[0].description)' x$($script:draft.items[0].quantity) @ $($script:draft.items[0].unitPrice)"
}

Check "AI draft due date = today + 14" {
    $expected = (Get-Date).AddDays(14).ToString("yyyy-MM-dd")
    Expect ($script:draft.dueDate -eq $expected) "dueDate was $($script:draft.dueDate), expected $expected"
    "dueDate=$($script:draft.dueDate)"
}

Check "AI validation rejects short prompt (400)" {
    $code = StatusCode POST "/ai/generate-invoice" @{ prompt = "short" }
    Expect ($code -eq 400) "expected 400 got $code"
    "status $code as expected"
}

Check "Malformed body rejected (400)" {
    $code = StatusCode POST "/invoices" @{ clientName = "X"; currency = "USD"; taxRate = 0; items = @{ description = "y"; quantity = 1; unitPrice = 1 } }
    Expect ($code -eq 400) "expected 400 got $code"
    "status $code as expected (items must be an array)"
}

# ---------------------------------------------------------------- invoice CRUD
$script:created = $null
Check "POST /invoices creates invoice" {
    $items = @($script:draft.items | ForEach-Object { @{ description = $_.description; quantity = $_.quantity; unitPrice = $_.unitPrice } })
    $script:created = Api POST "/invoices" @{
        clientName = $script:draft.clientName; clientEmail = $script:draft.clientEmail
        currency = "USD"; taxRate = $script:draft.taxRate; dueDate = $script:draft.dueDate
        notes = "Generated by SmartPay AI"; aiSource = $script:draft.source
        items = $items
    }
    Expect ($script:created.status -eq "DRAFT") "status was $($script:created.status)"
    Expect ($script:created.subtotal -eq 750) "subtotal was $($script:created.subtotal) expected 750.00"
    Expect ($script:created.taxAmount -eq 135) "taxAmount was $($script:created.taxAmount) expected 135.00"
    Expect ($script:created.total -eq 885) "total was $($script:created.total) expected 885.00"
    "$($script:created.invoiceNumber) subtotal=$($script:created.subtotal) tax=$($script:created.taxAmount) total=$($script:created.total)"
}

Check "GET /invoices lists" {
    $list = Api GET "/invoices"
    Expect ($list.Count -ge 1) "list empty"
    "$($list.Count) invoice(s)"
}

Check "GET /invoices/{id} returns detail" {
    $inv = Api GET "/invoices/$($script:created.id)"
    Expect ($inv.id -eq $script:created.id) "wrong invoice"
    "items=$($inv.items.Count) total=$($inv.total)"
}

Check "GET /invoices/999 returns 404 envelope" {
    $code = StatusCode GET "/invoices/999"
    Expect ($code -eq 404) "expected 404 got $code"
    "status $code as expected"
}

Check "PUT /invoices/{id} updates while DRAFT" {
    $updated = Api PUT "/invoices/$($script:created.id)" @{
        clientName = "Acme Corporation"; clientEmail = $script:created.clientEmail
        currency = "USD"; taxRate = 10; dueDate = $script:created.dueDate; notes = "updated"
        items = @(@{ description = "Web development"; quantity = 10; unitPrice = 75 })
    }
    Expect ($updated.clientName -eq "Acme Corporation") "name not updated"
    Expect ($updated.taxAmount -eq 75) "tax not recomputed: $($updated.taxAmount)"
    "total recomputed to $($updated.total)"
}

# ------------------------------------------------------- PDF download + email delivery
Check "GET /invoices/{id}/pdf returns a branded PDF (DRAFT)" {
    $r = Invoke-WebRequest -Uri "$base/invoices/$($script:created.id)/pdf" -Headers @{ Authorization = "Bearer $($script:token)" } -UseBasicParsing
    Expect ($r.StatusCode -eq 200) "status was $($r.StatusCode)"
    Expect ($r.Headers['Content-Type'] -match 'application/pdf') "content-type was $($r.Headers['Content-Type'])"
    $magic = [System.Text.Encoding]::ASCII.GetString($r.Content[0..4])
    Expect ($magic -eq '%PDF-') "PDF magic missing: '$magic'"
    "$($r.Content.Length) bytes, magic '$magic', disposition=$($r.Headers['Content-Disposition'])"
}

Check "GET /invoices/{id}/pdf without a token returns 401" {
    # Empty string (not $null) so the helper does not fall back to the session token.
    $code = StatusCode GET "/invoices/$($script:created.id)/pdf" $null ""
    Expect ($code -eq 401) "expected 401 got $code"
    "status $code as expected"
}

Check "POST /invoices/{id}/send reports NOT_CONFIGURED without SMTP" {
    $res = Api POST "/invoices/$($script:created.id)/send"
    Expect ($res.deliveryStatus -eq "NOT_CONFIGURED") "deliveryStatus was $($res.deliveryStatus)"
    Expect ($res.message -match "SMTP_HOST") "guidance should mention SMTP_HOST"
    Expect ($res.paypalMode -eq "NOT_CONFIGURED") "paypalMode was $($res.paypalMode)"
    Expect ($res.paypalLinkIncluded -eq $false) "must not claim a payment link without PayPal credentials"
    "status=$($res.deliveryStatus) paypal=$($res.paypalMode) - no email claimed as sent"
}

# ---------------------------------------------------------------- customers
$script:customer = $null
Check "POST /customers creates customer" {
    $script:customer = Api POST "/customers" @{ name = "Acme Corp"; email = "billing@acmecorp.com"; company = "Acme Corporation Inc."; city = "Austin"; country = "US" }
    Expect ($customer.id -gt 0) "no id"
    "customer id=$($customer.id)"
}

Check "GET /customers?search=acme" {
    $found = Api GET "/customers?search=acme"
    Expect ($found.Count -ge 1) "search found nothing"
    "$($found.Count) match(es)"
}

# ---------------------------------------------------------------- PayPal flow (SIMULATED without sandbox credentials)
$script:order = $null
Check "POST /invoices/{id}/paypal/order" {
    $script:order = Api POST "/invoices/$($script:created.id)/paypal/order"
    Expect ($script:order.mode -eq "SIMULATED" -or $script:order.mode -eq "LIVE") "unexpected mode"
    Expect ($script:order.orderId) "no order id"
    "mode=$($script:order.mode) orderId=$($script:order.orderId) invoiceStatus=$($script:order.invoiceStatus)"
}

Check "capture marks invoice PAID" {
    $paid = Api POST "/invoices/$($script:created.id)/paypal/capture"
    Expect ($paid.status -eq "PAID") "status was $($paid.status)"
    Expect ($paid.payment.status -eq "COMPLETED") "payment status was $($paid.payment.status)"
    "invoice $($paid.invoiceNumber) PAID via $($paid.payment.method) capture=$($paid.payment.paypalCaptureId)"
}

Check "GET /invoices/{id}/pdf works for a PAID invoice (receipt)" {
    $r = Invoke-WebRequest -Uri "$base/invoices/$($script:created.id)/pdf" -Headers @{ Authorization = "Bearer $($script:token)" } -UseBasicParsing
    Expect ($r.StatusCode -eq 200) "status was $($r.StatusCode)"
    $magic = [System.Text.Encoding]::ASCII.GetString($r.Content[0..4])
    Expect ($magic -eq '%PDF-') "PDF magic missing: '$magic'"
    "$($r.Content.Length) bytes for the paid receipt"
}

Check "paid invoice cannot be edited (400)" {
    $code = StatusCode PUT "/invoices/$($script:created.id)" @{ clientName = "X"; currency = "USD"; taxRate = 0; items = @(@{ description = "y"; quantity = 1; unitPrice = 1 }) }
    Expect ($code -eq 400) "expected 400 got $code"
    "status $code as expected"
}

Check "GET /payments lists the audit record" {
    $payments = Api GET "/payments"
    Expect ($payments.Count -ge 1) "no payments"
    $latest = $payments[0]
    Expect ($latest.status -eq "COMPLETED") "latest not COMPLETED"
    "$($payments.Count) payment(s), latest $($latest.method)/$($latest.status) amount=$($latest.amount) $($latest.currency)"
}

Check "GET /payments?invoiceId filter" {
    $list = Api GET "/payments?invoiceId=$($script:created.id)"
    Expect ($list.Count -ge 1) "filter empty"
    "$($list.Count) record(s) for invoice $($script:created.id)"
}

# ---------------------------------------------------------------- ownership isolation
$script:intruderEmail = "intruder-$(Get-Date -Format 'HHmmss')@smartpay.test"
$script:intruderPassword = "Intruder!123"
Check "A different user cannot read this invoice (404)" {
    $res = Api POST "/auth/register" @{ email = $script:intruderEmail; password = $script:intruderPassword; fullName = "Intruder" }
    Expect ($res.token) "intruder registration failed"
    $code = StatusCode GET "/invoices/$($script:created.id)" $null $res.token
    Expect ($code -eq 404) "expected 404 got $code (cross-user access must be blocked)"
    "status $code as expected (user isolation holds)"
}

Check "A different user sees an empty invoice list" {
    $res = Api POST "/auth/login" @{ email = $script:intruderEmail; password = $script:intruderPassword }
    Expect ($res.token) "intruder login failed"
    $list = Api GET "/invoices" $null $res.token
    Expect (@($list).Count -eq 0) "expected an empty list, got $(@($list).Count)"
    "0 invoices visible to the intruder"
}

Check "A different user cannot download this invoice PDF (404)" {
    $res = Api POST "/auth/login" @{ email = $script:intruderEmail; password = $script:intruderPassword }
    $code = StatusCode GET "/invoices/$($script:created.id)/pdf" $null $res.token
    Expect ($code -eq 404) "expected 404 got $code"
    "status $code as expected"
}

Check "A different user cannot send this invoice (404)" {
    $res = Api POST "/auth/login" @{ email = $script:intruderEmail; password = $script:intruderPassword }
    $code = StatusCode POST "/invoices/$($script:created.id)/send" $null $res.token
    Expect ($code -eq 404) "expected 404 got $code"
    "status $code as expected"
}

# ---------------------------------------------------------------- cleanup + summary
Check "DELETE /invoices/{id} on PAID returns 400 (audit retained)" {
    $code = StatusCode DELETE "/invoices/$($script:created.id)"
    Expect ($code -eq 400) "expected 400 got $code"
    "status $code as expected"
}

Write-Host ""
Write-Host ("SMOKE TEST: {0} passed, {1} failed" -f $script:passed, $script:failed) -ForegroundColor $(if ($script:failed -eq 0) { "Green" } else { "Red" })
if ($script:failed -gt 0) { exit 1 } else { exit 0 }

