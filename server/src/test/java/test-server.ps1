# PowerShell Test Script for Math Speed Server

param(
    [string]$ServerHost = "localhost",
    [int]$ServerPort = 8888,
    [switch]$Verbose
)

$ErrorActionPreference = "Stop"

function Write-TestHeader {
    param([string]$Message)
    Write-Host "`n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
    Write-Host "  $Message" -ForegroundColor Cyan
    Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
}

function Write-TestStep {
    param([string]$Message)
    Write-Host "`n▶ $Message" -ForegroundColor Yellow
}

function Write-Success {
    param([string]$Message)
    Write-Host "  ✅ $Message" -ForegroundColor Green
}

function Write-Failure {
    param([string]$Message)
    Write-Host "  ❌ $Message" -ForegroundColor Red
}

function Write-Info {
    param([string]$Message)
    Write-Host "  ℹ️  $Message" -ForegroundColor Blue
}

# Main Test Execution
Write-TestHeader "Math Speed Server - Automated Test Suite"
Write-Host "Target: $ServerHost`:$ServerPort`n" -ForegroundColor White

$testsPassed = 0
$testsFailed = 0

# Test 1: Check if server is listening
Write-TestStep "Test 1: Server Port Availability"
try {
    $connection = Test-NetConnection -ComputerName $ServerHost -Port $ServerPort -InformationLevel Quiet -WarningAction SilentlyContinue
    if ($connection) {
        Write-Success "Server is listening on port $ServerPort"
        $testsPassed++
    } else {
        Write-Failure "Server is NOT listening on port $ServerPort"
        $testsFailed++
        Write-Host "`n⚠️  Please start the server first:" -ForegroundColor Yellow
        Write-Host "   cd E:\Projects\Java\math-speed\server" -ForegroundColor Gray
        Write-Host "   java -jar target\server-1.0-SNAPSHOT.jar" -ForegroundColor Gray
        exit 1
    }
} catch {
    Write-Failure "Cannot check port: $_"
    $testsFailed++
    exit 1
}

# Test 2: TCP Socket Connection
Write-TestStep "Test 2: TCP Socket Connection"
try {
    $client = New-Object System.Net.Sockets.TcpClient
    $client.ReceiveTimeout = 5000
    $client.SendTimeout = 5000
    $client.Connect($ServerHost, $ServerPort)

    if ($client.Connected) {
        Write-Success "TCP connection established"
        $testsPassed++

        $stream = $client.GetStream()
        $writer = New-Object System.IO.StreamWriter($stream)
        $writer.AutoFlush = $true
        $reader = New-Object System.IO.StreamReader($stream)

        # Test 3: PING Command
        Write-TestStep "Test 3: PING Command"
        $writer.WriteLine("PING")
        Start-Sleep -Milliseconds 500

        if ($stream.DataAvailable) {
            $response = $reader.ReadLine()
            if ($Verbose) {
                Write-Info "Response: $response"
            }
            if ($response -match "PONG") {
                Write-Success "PING command successful"
                $testsPassed++
            } else {
                Write-Failure "Unexpected response: $response"
                $testsFailed++
            }
        } else {
            Write-Failure "No response from server"
            $testsFailed++
        }

        # Test 4: REGISTER Command
        Write-TestStep "Test 4: REGISTER Command"
        $testUser = "testuser_$(Get-Random -Maximum 9999)"
        $testPass = "testpass123"

        $writer.WriteLine("REGISTER $testUser $testPass male")
        Start-Sleep -Milliseconds 800

        if ($stream.DataAvailable) {
            $response = $reader.ReadLine()
            if ($Verbose) {
                Write-Info "Response: $response"
            }
            if ($response -match "REGISTER_SUCCESS|already exists") {
                Write-Success "REGISTER command processed"
                $testsPassed++
            } else {
                Write-Failure "REGISTER failed: $response"
                $testsFailed++
            }
        }

        # Test 5: LOGIN Command
        Write-TestStep "Test 5: LOGIN Command"
        $writer.WriteLine("LOGIN $testUser $testPass")
        Start-Sleep -Milliseconds 800

        if ($stream.DataAvailable) {
            $response = $reader.ReadLine()
            if ($Verbose) {
                Write-Info "Response: $response"
            }
            if ($response -match "LOGIN_SUCCESS") {
                Write-Success "LOGIN command successful"
                $testsPassed++

                # Read any additional responses (like PLAYER_LIST_UPDATE)
                Start-Sleep -Milliseconds 300
                while ($stream.DataAvailable) {
                    $extraResponse = $reader.ReadLine()
                    if ($Verbose) {
                        Write-Info "Additional response: $extraResponse"
                    }
                }
            } else {
                Write-Failure "LOGIN failed: $response"
                $testsFailed++
            }
        }

        # Test 6: JOIN_QUEUE Command
        Write-TestStep "Test 6: JOIN_QUEUE Command"
        $writer.WriteLine("JOIN_QUEUE")
        Start-Sleep -Milliseconds 500

        if ($stream.DataAvailable) {
            $response = $reader.ReadLine()
            if ($Verbose) {
                Write-Info "Response: $response"
            }
            if ($response -match "QUEUE_JOINED") {
                Write-Success "JOIN_QUEUE command successful"
                $testsPassed++
            } else {
                Write-Failure "JOIN_QUEUE failed: $response"
                $testsFailed++
            }
        }

        # Cleanup
        $writer.WriteLine("QUIT")
        Start-Sleep -Milliseconds 200

        $reader.Close()
        $writer.Close()
        $stream.Close()
        $client.Close()

    } else {
        Write-Failure "Cannot establish TCP connection"
        $testsFailed++
    }

} catch {
    Write-Failure "Socket connection error: $_"
    $testsFailed++
} finally {
    if ($null -ne $client -and $client.Connected) {
        $client.Close()
    }
}

# Test Summary
Write-TestHeader "Test Summary"
Write-Host "  Total Tests: $($testsPassed + $testsFailed)" -ForegroundColor White
Write-Host "  Passed:      $testsPassed" -ForegroundColor Green
Write-Host "  Failed:      $testsFailed" -ForegroundColor Red

if ($testsFailed -eq 0) {
    Write-Host "`n🎉 All tests passed!" -ForegroundColor Green
    exit 0
} else {
    Write-Host "`n⚠️  Some tests failed. Please check server logs." -ForegroundColor Yellow
    Write-Host "   Server logs location: E:\Projects\Java\math-speed\server\logs\server.log" -ForegroundColor Gray
    exit 1
}

