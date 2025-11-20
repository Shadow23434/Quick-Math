# 🚀 Quick Test Guide - Math Speed Server

## Chuẩn bị trước khi test

### 1️⃣ Đảm bảo database đã ready

```sql
mysql -u root -p
CREATE DATABASE IF NOT EXISTS quickmath;
USE quickmath;
source src/sql/quickmath.sql;  -- hoặc chạy manual create tables
```

### 2️⃣ Build server

```powershell
mvn clean package -DskipTests
```

---

## 🎯 3 Cách Test Nhanh

### Cách 1: Automated Test (Khuyến nghị cho CI/CD)

```powershell
# Terminal 1: Start server
java -jar target\server-1.0-SNAPSHOT.jar

# Terminal 2: Run tests
.\test-server.ps1

# Hoặc với verbose output
.\test-server.ps1 -Verbose
```

**Kỳ vọng:**
```
✅ Server is listening on port 8888
✅ TCP connection established
✅ PING command successful
✅ REGISTER command processed
✅ LOGIN command successful
✅ JOIN_QUEUE command successful

🎉 All tests passed!
```

---

### Cách 2: Interactive Test Client (Khuyến nghị cho manual testing)

```powershell
# Terminal 1: Start server
java -jar target\server-1.0-SNAPSHOT.jar

# Terminal 2: Compile và run client
javac TestClient.java
java TestClient
```

**Test scenarios:**

**Scenario A: Basic Auth Flow**
```
>>> PING
<<< SERVER: PONG

>>> REGISTER alice pass123 female
<<< SERVER: REGISTER_SUCCESS|Account created successfully

>>> LOGIN alice pass123
<<< SERVER: LOGIN_SUCCESS
<<< SERVER: PLAYER_LIST_UPDATE|alice:ONLINE
```

**Scenario B: Matchmaking**
```
>>> LOGIN alice pass123
<<< SERVER: LOGIN_SUCCESS

>>> JOIN_QUEUE
<<< SERVER: QUEUE_JOINED
```

**Scenario C: Challenge Flow** (cần 2 clients)
```
# Client 1 (Alice)
>>> LOGIN alice pass123
>>> CHALLENGE bob 5
<<< SERVER: CHALLENGE_SENT|bob|5

# Client 2 (Bob) - terminal khác
>>> LOGIN bob pass456
<<< SERVER: CHALLENGE_REQUEST|alice|5
>>> ACCEPT alice
<<< SERVER: CHALLENGE_ACCEPTED|alice|5
<<< SERVER: INFO|Game starts in 5 seconds...
```

---

### Cách 3: Manual Telnet Test (Khuyến nghị cho debug connection)

```powershell
# Terminal 1: Start server
java -jar target\server-1.0-SNAPSHOT.jar

# Terminal 2: Connect với telnet
telnet localhost 8888
```

**Commands:**
```
PING
REGISTER test pass123 male
LOGIN test pass123
JOIN_QUEUE
quit
```

---

## 🔍 Quick Diagnostics

### Kiểm tra server đang chạy

```powershell
# Check port
Test-NetConnection localhost -Port 8888

# Check process
Get-Process -Name java | Where-Object {$_.MainWindowTitle -match "server"}

# Check logs
Get-Content logs\server.log -Tail 20
```

### Troubleshooting common issues

**Issue: "Connection refused"**
```powershell
# Server chưa start hoặc port bị chiếm
netstat -an | Select-String "8888"

# Nếu có process khác, kill nó
Get-NetTCPConnection -LocalPort 8888 | Select-Object OwningProcess
Stop-Process -Id <PID> -Force
```

**Issue: "Database connection failed"**
```powershell
# Verify MySQL running
Get-Service MySQL* | Select-Object Name, Status

# Test DB connection
mysql -u root -p -e "USE quickmath; SHOW TABLES;"
```

**Issue: "Class not found"**
```powershell
# Rebuild with dependencies
mvn clean package -DskipTests

# Verify JAR
jar tf target\server-1.0-SNAPSHOT.jar | Select-String "mysql"
```

---

## 📊 Load Testing (Optional)

### Test multiple concurrent connections

```powershell
# Test với 10 concurrent clients
1..10 | ForEach-Object -Parallel {
    $client = New-Object System.Net.Sockets.TcpClient("localhost", 8888)
    $stream = $client.GetStream()
    $writer = New-Object System.IO.StreamWriter($stream)
    $writer.AutoFlush = $true
    
    $writer.WriteLine("PING")
    Start-Sleep -Milliseconds 500
    
    $writer.Close()
    $stream.Close()
    $client.Close()
    
    Write-Host "Client $_ completed" -ForegroundColor Green
} -ThrottleLimit 10
```

---

## ✅ Verification Checklist

Trước khi production:

- [ ] Database tables đã tạo và có indexes
- [ ] Server compile thành công không có warnings
- [ ] Port 8888 listening và accessible
- [ ] PING/PONG working
- [ ] REGISTER tạo user mới trong database
- [ ] LOGIN authenticate chính xác
- [ ] JOIN_QUEUE không throw exceptions
- [ ] CHALLENGE/ACCEPT flow hoàn chỉnh
- [ ] Game session tạo và kết thúc đúng
- [ ] Logs không có ERROR nghiêm trọng
- [ ] Connection pool không bị exhausted
- [ ] Graceful shutdown working

---

## 🎓 Protocol Quick Reference

### Message Format
```
<TYPE>|<payload>
hoặc
<TYPE>
```

### Common MessageTypes
```
PING, PONG                    - Keep-alive
LOGIN_SUCCESS, LOGIN_FAILED   - Auth results
QUEUE_JOINED, QUEUE_LEFT      - Queue status
CHALLENGE_REQUEST, CHALLENGE_ACCEPTED, CHALLENGE_DECLINED
GAME_START, NEW_QUESTION, ANSWER_RESULT, GAME_END
ERROR|<message>               - Error response
```

---

## 📞 Support

**Logs location:** `E:\Projects\Java\math-speed\server\logs\server.log`

**View live logs:**
```powershell
Get-Content logs\server.log -Tail 50 -Wait
```

**Full documentation:** See `TESTING_GUIDE.md`

---

**Version:** 1.0  
**Date:** November 20, 2025  
**Project:** Math Speed Server (Hexagonal Architecture)

