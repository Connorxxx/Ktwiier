# JWT 认证测试指南

## 快速开始

### 1. 在 App.kt 中测试注册界面

将 `App.kt` 修改为显示注册界面：

```kotlin
package com.connor.kwitter

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.connor.kwitter.features.auth.RegisterScreen

@Composable
fun App() {
    MaterialTheme {
        RegisterScreen(
            onRegisterSuccess = { token ->
                println("注册成功! Token: $token")
            }
        )
    }
}
```

### 2. 运行 Android App

```bash
./gradlew :androidApp:installDebug
```

或在 Android Studio 中直接运行 `androidApp` 模块。

### 3. 测试注册功能

1. 在注册界面输入：
   - Email: `test@example.com`
   - Name: `测试用户`
   - Password: `password123`

2. 点击"注册"按钮

3. 观察结果：
   - 成功：显示 "注册成功！Token: xxx..."
   - 失败：显示错误信息（网络错误、服务器错误等）

## 手动测试 Repository

### 在 ViewModel 或其他地方直接测试

```kotlin
import com.connor.kwitter.domain.auth.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class TestAuthRepository : KoinComponent {
    private val authRepository: AuthRepository by inject()

    fun testRegister() {
        CoroutineScope(Dispatchers.Main).launch {
            // 测试注册
            println("开始测试注册...")
            when (val result = authRepository.register(
                email = "test@example.com",
                name = "测试用户",
                password = "password123"
            )) {
                is arrow.core.Either.Left -> {
                    println("注册失败: ${result.value}")
                }
                is arrow.core.Either.Right -> {
                    println("注册成功! Token: ${result.value.token}")

                    // 测试获取存储的 token
                    testGetToken()
                }
            }
        }
    }

    suspend fun testGetToken() {
        println("测试获取存储的 Token...")
        when (val result = authRepository.getStoredToken()) {
            is arrow.core.Either.Left -> {
                println("获取失败: ${result.value}")
            }
            is arrow.core.Either.Right -> {
                val token = result.value
                if (token != null) {
                    println("Token 已存储: ${token.token}")
                } else {
                    println("没有存储的 Token")
                }
            }
        }
    }

    suspend fun testClearToken() {
        println("测试清除 Token...")
        when (val result = authRepository.clearToken()) {
            is arrow.core.Either.Left -> {
                println("清除失败: ${result.value}")
            }
            is arrow.core.Either.Right -> {
                println("Token 已清除")
            }
        }
    }
}
```

## 使用 cURL 测试服务器

在运行应用前，先用 cURL 测试服务器是否可访问：

```bash
curl -X POST http://192.168.12.123/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "name": "测试用户",
    "password": "password123"
  }'
```

预期响应：
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

## 常见问题排查

### 1. 网络连接失败

**问题**: `NetworkError: Network request failed`

**解决方案**:
- 确保设备/模拟器能访问 `192.168.12.123`
- Android 模拟器访问本机：使用 `10.0.2.2` 而不是 `localhost` 或 `127.0.0.1`
- 真机测试：确保手机和服务器在同一网络
- 检查防火墙设置

### 2. HTTP 明文流量被阻止（Android 9+）

**问题**: `java.net.UnknownServiceException: CLEARTEXT communication not permitted`

**解决方案**: 创建网络安全配置文件

在 `androidApp/src/main/res/xml/network_security_config.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">192.168.12.123</domain>
    </domain-config>
</network-security-config>
```

在 `AndroidManifest.xml` 中引用：
```xml
<application
    android:networkSecurityConfig="@xml/network_security_config"
    ...>
```

### 3. iOS 不允许 HTTP 连接

**解决方案**: 在 `iosApp/Info.plist` 添加：
```xml
<key>NSAppTransportSecurity</key>
<dict>
    <key>NSAllowsArbitraryLoads</key>
    <false/>
    <key>NSExceptionDomains</key>
    <dict>
        <key>192.168.12.123</key>
        <dict>
            <key>NSExceptionAllowsInsecureHTTPLoads</key>
            <true/>
        </dict>
    </dict>
</dict>
```

### 4. DataStore 读写错误

**问题**: `StorageError: Failed to read/write token`

**解决方案**:
- 检查 Android 存储权限
- 确保 Koin 正确初始化（platformModule 已加载）
- 清除应用数据后重试

### 5. Koin 依赖注入失败

**问题**: `NoBeanDefFoundException` 或 ViewModel 无法创建

**解决方案**:
- 确保 `KwitterApplication` 在 `AndroidManifest.xml` 中配置
- 确保所有模块都在 `startKoin` 中加载
- iOS: 确保在 `MainViewController` 中调用 `initKoin()`

## 日志调试

### 启用 Ktor 详细日志

已在 `NetworkModule.kt` 中配置 `LogLevel.BODY`，可以查看完整的 HTTP 请求和响应：

```
[Ktor] REQUEST: POST http://192.168.12.123/v1/auth/register
[Ktor] BODY: {"email":"test@example.com","name":"测试用户","password":"password123"}
[Ktor] RESPONSE: 200 OK
[Ktor] BODY: {"token":"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."}
```

### Android Logcat 过滤

```bash
adb logcat | grep -E "(Ktor|Koin|Auth)"
```

### 查看 DataStore 文件（Android）

```bash
adb shell run-as com.connor.kwitter ls -la /data/data/com.connor.kwitter/files/datastore/
adb shell run-as com.connor.kwitter cat /data/data/com.connor.kwitter/files/datastore/kwitter_preferences.preferences_pb
```

## 单元测试示例

```kotlin
// commonTest/kotlin/com/connor/kwitter/domain/auth/AuthRepositoryTest.kt
import com.connor.kwitter.domain.auth.repository.AuthRepository
import kotlinx.coroutines.test.runTest
import org.koin.test.KoinTest
import org.koin.test.inject
import kotlin.test.Test
import kotlin.test.assertTrue

class AuthRepositoryTest : KoinTest {
    private val authRepository: AuthRepository by inject()

    @Test
    fun `register with valid credentials returns token`() = runTest {
        val result = authRepository.register(
            email = "test@example.com",
            name = "Test User",
            password = "password123"
        )

        assertTrue(result.isRight())
    }

    @Test
    fun `save and retrieve token works`() = runTest {
        val token = AuthToken("test_token_12345")

        authRepository.saveToken(token)
        val retrieved = authRepository.getStoredToken()

        assertTrue(retrieved.isRight())
        assertTrue(retrieved.getOrNull() == token)
    }
}
```

## 集成测试清单

- [ ] 注册成功流程
- [ ] Token 自动保存到 DataStore
- [ ] 获取已保存的 Token
- [ ] 清除 Token
- [ ] 网络错误处理（飞行模式）
- [ ] 服务器错误处理（5xx）
- [ ] 客户端错误处理（4xx）
- [ ] 无效输入处理（空字符串）
- [ ] 多次连续注册
- [ ] 应用重启后 Token 持久化

## 性能测试

### 测试 Token 读取性能

```kotlin
import kotlin.system.measureTimeMillis

val time = measureTimeMillis {
    repeat(1000) {
        authRepository.getStoredToken()
    }
}
println("1000 次读取耗时: ${time}ms")
```

### 测试网络请求性能

```kotlin
val time = measureTimeMillis {
    authRepository.register("test@example.com", "user", "pass")
}
println("注册请求耗时: ${time}ms")
```

## 下一步

完成基本测试后，可以添加：
1. 登录接口测试
2. Token 刷新测试
3. 多平台一致性测试（Android + iOS）
4. 并发请求测试
5. 离线模式测试
