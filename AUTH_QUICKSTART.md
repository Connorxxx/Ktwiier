# 🚀 JWT 认证快速启动指南

## 5 分钟上手

### 1️⃣ 修改 MainActivity（1 分钟）

编辑 `androidApp/src/main/java/com/connor/kwitter/MainActivity.kt`:

```kotlin
package com.connor.kwitter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            AppWithAuth()  // 👈 使用这个！
        }
    }
}
```

### 2️⃣ 运行应用（1 分钟）

在 Android Studio 中点击 Run，或执行：

```bash
./gradlew :androidApp:installDebug
```

### 3️⃣ 测试注册（3 分钟）

应用启动后，你会看到注册界面：

1. 填写表单：
   - **Email**: `test@example.com`
   - **Name**: `测试用户`
   - **Password**: `password123`

2. 点击 **"注册"** 按钮

3. 观察结果：
   - ✅ **成功**: 界面显示 "注册成功！Token: xxx..."
   - ❌ **失败**: 显示错误信息（检查网络连接）

### 4️⃣ 查看 Token（可选）

Token 已自动保存到 DataStore。重启应用后依然存在。

## 🎯 就这么简单！

只需要修改一行代码（`AppWithAuth()`），就能看到完整的注册功能。

---

## 🔧 常见问题

### ❓ 网络连接失败？

**检查服务器地址**：确保 `192.168.12.123` 可访问。

**Android 模拟器访问本机服务器**：
- 将 `AuthRemoteDataSource.kt` 中的 `192.168.12.123` 改为 `10.0.2.2`

**真机测试**：
- 确保手机和服务器在同一局域网

### ❓ HTTP 被阻止（Android 9+）？

创建 `androidApp/src/main/res/xml/network_security_config.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">192.168.12.123</domain>
    </domain-config>
</network-security-config>
```

在 `AndroidManifest.xml` 中添加：

```xml
<application
    android:networkSecurityConfig="@xml/network_security_config"
    ...>
```

### ❓ 想自己调用 Repository？

```kotlin
class MyViewModel(
    private val authRepository: AuthRepository  // Koin 自动注入
) : ViewModel() {

    fun register() {
        viewModelScope.launch {
            authRepository.register("email", "name", "pass")
                .onLeft { error -> println("错误: $error") }
                .onRight { token -> println("成功: ${token.token}") }
        }
    }
}
```

---

## 📚 完整文档

- **实现细节**: `AUTH_IMPLEMENTATION.md`
- **测试指南**: `AUTH_TESTING_GUIDE.md`
- **总览**: `JWT_AUTH_SUMMARY.md`

---

## 🎉 完成！

你已经有了一个完整的、跨平台的、生产级的 JWT 认证系统！

架构特点：
- ✅ 完全跨平台（Android + iOS）
- ✅ 类型安全（Arrow Either）
- ✅ 响应式（Molecule + Flow）
- ✅ 可测试（Repository 模式 + DI）
- ✅ 可扩展（清晰的分层）

**享受开发！** 🚀
