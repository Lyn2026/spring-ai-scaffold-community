param(
    [string]$Base = "http://localhost:8080/api/ai",
    [string]$Tenant = "demo"
)

# 逐个模型打一次真实对话，确认「配置的模型名在该端点上确实存在」。
# 用法：先启动 demo，再在同一个窗口执行
#   .\scripts\check-models.ps1
#   .\scripts\check-models.ps1 -Base http://localhost:8082/api/ai

$headers = @{ "X-Tenant-Id" = $Tenant }

function Get-ErrorBody($errorRecord) {
    try {
        $response = $errorRecord.Exception.Response
        if ($null -ne $response) {
            $stream = $response.GetResponseStream()
            $reader = New-Object System.IO.StreamReader($stream)
            return $reader.ReadToEnd()
        }
    } catch {
    }
    return $errorRecord.Exception.Message
}

function Shorten([string]$text, [int]$max) {
    if ([string]::IsNullOrEmpty($text)) { return "" }
    $flat = $text -replace "`r?`n", " "
    if ($flat.Length -gt $max) { return $flat.Substring(0, $max) + "..." }
    return $flat
}

Write-Output ("探测 {0}/models ..." -f $Base)
try {
    $models = (Invoke-RestMethod -Uri "$Base/models" -Headers $headers -TimeoutSec 30).data.chat
} catch {
    Write-Output ("无法读取模型列表，请确认服务已启动：" + (Get-ErrorBody $_))
    exit 1
}

$failed = 0
foreach ($m in $models) {
    $body = @{
        question = "Reply with OK only."
        model    = $m.name
        useRag   = $false
        useTools = $false
    } | ConvertTo-Json

    try {
        $r = Invoke-RestMethod -Uri "$Base/chat" -Method Post -Headers $headers `
                -ContentType "application/json" -Body $body -TimeoutSec 60
        $answer = Shorten $r.data.answer 80
        Write-Output ("PASS  {0,-10} {1,-22} {2}" -f $m.name, $m.model, $answer)
    } catch {
        $failed++
        $detail = Shorten (Get-ErrorBody $_) 260
        Write-Output ("FAIL  {0,-10} {1,-22} {2}" -f $m.name, $m.model, $detail)
    }
}

Write-Output ""
if ($failed -gt 0) {
    Write-Output ("有 {0} 个模型调用失败。看报错里的 url：compatible-mode 端点要用公共模型名；/api/v1 端点要用部署里的模型名。" -f $failed)
    exit 1
}
Write-Output "全部模型调用成功。"
