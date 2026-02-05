# JWT 认证实现文档

## 架构概览

本项目采用 KMP (Kotlin Multiplatform) 架构实现了 JWT 认证功能，遵循以下分层：

```
┌─────────────────────────────────────┐
│         UI Layer (Compose)          │
│   RegisterScreen + RegisterViewModel │
└─────────────────────────────────────┘
                  ↓
┌─────────────────────────────────────┐
│      Domain Layer (Interface)       │
│      AuthRepository (interface)     │
└─────────────────────────────────────┘
                  ↓
┌─────────────────────────────────────┐
│       Data Layer (Implementation)   │
│  AuthRepositoryImpl + DataSources   │
└─────────────────────────────────────┘
         ↙                    ↘
┌──────────────────┐  ┌──────────────────┐
│ TokenDataSource  │  │ AuthRemoteDataSource │
│  (DataStore)     │  │   (Ktor Client)     │
└──────────────────┘  └──────────────────┘
```

## 文件结构

### Domain 层 (commonMain)
- `domain/auth/model/AuthToken.kt` - 认证令牌数据类
- `domain/auth/model/RegisterRequest.kt` - 注册请求数据类
- `domain/auth/model/RegisterResponse.kt` - 注册响应数据类
- `domain/auth/model/AuthError.kt` - 错误类型定义
- `domain/auth/repository/AuthRepository.kt` - 仓储接口

### Data 层 (commonMain)
- `data/datastore/DataStoreFactory.kt` - DataStore 工厂函数定义
- `data/auth/datasource/TokenDataSource.kt` - Token 本地存储
- `data/auth/datasource/AuthRemoteDataSource.kt` - 网络请求处理
- `data/auth/repository/AuthRepositoryImpl.kt` - 仓储实现

### DI 模块 (commonMain)
- `core/di/NetworkModule.kt` - HttpClient 配置
- `core/di/AuthModule.kt` - 认证相关依赖注入
- `core/di/ViewModelModule.kt` - ViewModel 依赖注入

### Feature 层 (commonMain)
- `features/auth/RegisterViewModel.kt` - 注册界面 ViewModel
- `features/auth/RegisterScreen.kt` - 注册界面 UI

### 平台特定实现
- `androidMain/kotlin/.../di/PlatformModule.android.kt` - Android DataStore 实现
- `iosMain/kotlin/.../di/PlatformModule.ios.kt` - iOS DataStore 实现
- `iosMain/kotlin/.../di/KoinHelper.kt` - iOS Koin 初始化

### Android 入口
- `androidApp/src/main/java/.../KwitterApplication.kt` - Android Application
- `androidApp/src/main/AndroidManifest.xml` - 已配置 INTERNET 权限和 Application

## 使用方式

### 1. 在 Android 中使用

`KwitterApplication` 已自动初始化所有依赖，直接在 Composable 中使用：

```kotlin
@Composable
fun MyScreen() {
    RegisterScreen(
        onRegisterSuccess = { token ->
            // 注册成功，获得 token
            println("Token: $token")
        }
    )
}
```

### 2. 在 iOS 中使用

在 `MainViewController.kt` 中初始化 Koin：

```kotlin
import com.connor.kwitter.di.initKoin

@OptIn(ExperimentalForeignApi::class)
fun MainViewController() = ComposeUIViewController(
    configure = {
        initKoin()  // 初始化 Koin
    }
) {
    App()
}
```

### 3. 直接使用 Repository（在 ViewModel 中）

```kotlin
class MyViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    fun register(email: String, name: String, password: String) {
        viewModelScope.launch {
            when (val result = authRepository.register(email, name, password)) {
                is Either.Left -> {
                    // 错误处理
                    val error: AuthError = result.value
                    handleError(error)
                }
                is Either.Right -> {
                    // 成功
                    val token: AuthToken = result.value
                    println("Token: ${token.token}")
                }
            }
        }
    }

    fun getStoredToken() {
        viewModelScope.launch {
            when (val result = authRepository.getStoredToken()) {
                is Either.Left -> {
                    // 错误处理
                }
                is Either.Right -> {
                    val token: AuthToken? = result.value
                    if (token != null) {
                        println("Stored token: ${token.token}")
                    } else {
                        println("No token stored")
                    }
                }
            }
        }
    }
}
```

## API 端点

### 注册接口
- **URL**: `http://192.168.12.123/v1/auth/register`
- **Method**: POST
- **Content-Type**: application/json
- **Request Body**:
```json
{
  "email": "user@example.com",
  "name": "用户名",
  "password": "密码"
}
```
- **Response**:
```json
{
  "token": "jwt_token_string"
}
```

## 错误处理

使用 Arrow 的 `Either` 类型进行函数式错误处理：

```kotlin
sealed class AuthError {
    data class NetworkError(val message: String) : AuthError()
    data class ServerError(val code: Int, val message: String) : AuthError()
    data class ClientError(val code: Int, val message: String) : AuthError()
    data class InvalidCredentials(val message: String) : AuthError()
    data class StorageError(val message: String) : AuthError()
    data class Unknown(val message: String) : AuthError()
}
```

所有 `AuthRepository` 方法返回 `Either<AuthError, T>`：
- `Either.Left(error)` - 操作失败，包含错误信息
- `Either.Right(value)` - 操作成功，包含结果

## Token 存储

Token 使用 DataStore Preferences 存储：
- **Key**: `auth_token`
- **存储位置**:
  - Android: `/data/data/com.connor.kwitter/files/datastore/kwitter_preferences.preferences_pb`
  - iOS: Documents 目录下的 `kwitter_preferences.preferences_pb`

## 依赖说明

所有依赖已在 `libs.versions.toml` 中配置：
- **Ktor Client**: 3.4.0 - HTTP 网络请求
- **DataStore**: 1.2.0 - 本地数据持久化
- **Arrow**: 2.2.1.1 - 函数式错误处理
- **Koin**: 4.1.1 - 依赖注入
- **Molecule**: 2.2.0 - 响应式状态管理

## 注意事项

1. **网络配置**: 确保设备能访问 `192.168.12.123`
2. **Android 权限**: 已在 `AndroidManifest.xml` 添加 `INTERNET` 权限
3. **iOS 配置**: 需要在 `Info.plist` 中配置 App Transport Security 允许 HTTP 连接
4. **线程安全**: 所有 Repository 方法都是 `suspend` 函数，自动在协程中执行
5. **错误处理**: 使用 Arrow Either，避免抛出异常

## 测试建议

1. 在真实设备或模拟器上测试网络连接
2. 测试各种错误场景（网络断开、服务器错误等）
3. 验证 Token 存储和读取
4. 测试多平台一致性（Android + iOS）

## 扩展建议

未来可以添加：
1. 登录接口 (`/v1/auth/login`)
2. Token 刷新机制
3. 自动在 HTTP 请求中附加 Token (Ktor Auth Plugin)
4. Token 过期检测和自动登出
5. 生物识别认证集成
