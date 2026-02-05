# Kwitter - KMP 项目

这是一个配置完整的 Kotlin Multiplatform (KMP) 项目，包含现代 Android/iOS 开发所需的所有核心依赖。

## 🎯 已配置的技术栈

### 核心框架
- **Kotlin** 2.3.0
- **Compose Multiplatform** 1.10.0
- **KSP** 2.3.4 - 代码生成

### 数据层
- **Room** 2.8.4 - SQLite ORM (KMP 支持)
- **DataStore** 1.2.0-alpha01 - 键值对存储
- **Ktor** 3.0.3 - HTTP 客户端
- **kotlinx-serialization** 1.8.0 - JSON & ProtoBuf 序列化

### 并发与异步
- **Coroutines** 1.10.1 - 协程
- **Flow** - 响应式数据流

### 函数式编程
- **Arrow** 2.1.0
  - Either/Option - 错误处理
  - Optics - 数据操作
  - Resilience - 重试/熔断

### 架构组件
- **Koin** 4.1.0 - 依赖注入
- **Navigation** 2.9.0-alpha05 - 类型安全导航
- **Molecule** 2.0.0 - 状态管理
- **ViewModel** - MVVM 支持

### 工具库
- **kotlinx-datetime** - 日期时间
- **kotlinx-atomicfu** - 原子操作
- **kotlinx-io** - IO 操作

## 📁 项目结构

```
composeApp/
├── src/
│   ├── commonMain/          # 共享代码
│   │   └── kotlin/
│   │       ├── di/          # Koin 依赖注入
│   │       ├── data/        # 数据层 (Room + DataStore)
│   │       ├── navigation/  # 导航配置
│   │       ├── ui/          # UI 组件
│   │       └── utils/       # 工具类
│   ├── androidMain/         # Android 平台代码
│   └── iosMain/             # iOS 平台代码
└── schemas/                 # Room 数据库 schema
```

## 🚀 快速开始

### 1. 同步依赖

```bash
./gradlew --refresh-dependencies
```

### 2. 构建项目

```bash
# Android
./gradlew :composeApp:assembleDebug

# iOS
./gradlew :composeApp:linkDebugFrameworkIosArm64
```

### 3. 生成 KSP 代码

```bash
# Room + Arrow Optics
./gradlew kspCommonMainKotlinMetadata
./gradlew kspAndroid
./gradlew kspIosArm64
```

## 💡 使用示例

### 依赖注入 (Koin)

```kotlin
// 1. 在应用启动时初始化
fun main() {
    initKoin()
}

// 2. 在 Composable 中使用
@Composable
fun MyScreen() {
    val viewModel = koinViewModel<MyViewModel>()
    val httpClient: HttpClient = koinInject()
}
```

### 数据库 (Room)

```kotlin
// 1. 定义 Entity
@Entity
data class Tweet(
    @PrimaryKey val id: String,
    val content: String,
    val userId: String
)

// 2. 定义 DAO
@Dao
interface TweetDao {
    @Query("SELECT * FROM Tweet")
    fun getAllTweets(): Flow<List<Tweet>>

    @Insert
    suspend fun insert(tweet: Tweet)
}

// 3. 在 AppDatabase 中添加
@Database(entities = [Tweet::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tweetDao(): TweetDao
}
```

### 网络请求 (Ktor)

```kotlin
class TweetRepository(private val client: HttpClient) {
    suspend fun fetchTweets(): List<Tweet> {
        return client.get("https://api.example.com/tweets").body()
    }
}
```

### 错误处理 (Arrow)

```kotlin
suspend fun fetchUserSafely(id: String): Either<AppError, User> =
    runCatchingEither {
        api.getUser(id)
    }

// 使用
fetchUserSafely("123").fold(
    ifLeft = { error -> /* 处理错误 */ },
    ifRight = { user -> /* 使用数据 */ }
)
```

### 导航 (Navigation)

```kotlin
// 1. 定义路由
@Serializable
data class ProfileRoute(val userId: String)

// 2. 配置导航图
NavHost(navController, startDestination = HomeRoute) {
    composable<ProfileRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<ProfileRoute>()
        ProfileScreen(userId = route.userId)
    }
}

// 3. 导航
navController.navigate(ProfileRoute(userId = "123"))
```

### 状态管理 (Molecule)

```kotlin
@Composable
fun CounterPresenter(events: Flow<CounterEvent>): CounterState {
    var count by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        events.collect { event ->
            when (event) {
                CounterEvent.Increment -> count++
                CounterEvent.Decrement -> count--
            }
        }
    }

    return CounterState(count)
}

// 在 ViewModel 中使用
val stateFlow = scope.launchMolecule {
    CounterPresenter(events)
}
```

### 数据存储 (DataStore)

```kotlin
class SettingsRepository(private val dataStore: DataStore<Preferences>) {
    val isDarkMode: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[DARK_MODE_KEY] ?: false
    }

    suspend fun setDarkMode(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[DARK_MODE_KEY] = enabled
        }
    }

    companion object {
        private val DARK_MODE_KEY = booleanPreferencesKey("dark_mode")
    }
}
```

## 📚 详细文档

- **DEPENDENCIES.md** - 所有依赖的详细说明和用法
- **SETUP_SUMMARY.md** - 配置总结和架构建议
- **CONFIG_CHECKLIST.md** - 配置清单和验证步骤

## 🏗️ 推荐架构

```
UI Layer (Compose)
    ↓
Presentation Layer (ViewModel + Molecule)
    ↓
Domain Layer (Use Cases + Arrow)
    ↓
Data Layer (Repository + Room + Ktor + DataStore)
```

### 模块分层

```kotlin
// Data Module
val dataModule = module {
    single { get<AppDatabase>().tweetDao() }
    single { TweetRepository(get(), get()) }
}

// Domain Module
val domainModule = module {
    factory { GetTweetsUseCase(get()) }
    factory { PostTweetUseCase(get()) }
}

// Presentation Module
val presentationModule = module {
    viewModel { TweetListViewModel(get(), get()) }
}
```

## 🔧 配置说明

### Gradle 配置

所有依赖版本统一在 `gradle/libs.versions.toml` 中管理：

```toml
[versions]
kotlin = "2.3.0"
ksp = "2.3.4"
room = "2.8.4"
ktor = "3.0.3"
koin = "4.1.0"
arrow = "2.1.0"
# ... 更多版本
```

### KSP 配置

KSP 用于代码生成（Room、Arrow Optics）：

```kotlin
dependencies {
    // Room
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)

    // Arrow Optics
    add("kspCommonMainMetadata", libs.arrow.optics.ksp.plugin)
}
```

## ⚠️ 注意事项

1. **Room Schema**: 数据库 schema 自动导出到 `composeApp/schemas` 目录
2. **Platform Specific**: DataStore 和 Room 需要平台特定实现（已提供）
3. **Koin 初始化**: 必须在应用启动时调用 `initKoin()`
4. **KSP 生成**: 修改 Entity 后需要重新运行 KSP 任务

## 🎯 下一步

1. **创建数据模型**: 定义 Entity、DTO 和领域模型
2. **实现 Repository**: 结合 Room 和 Ktor 实现数据层
3. **构建 UI**: 使用 Compose 创建界面
4. **添加业务逻辑**: 实现 Use Cases 和 ViewModel
5. **测试**: 使用 Koin Test 和 Coroutines Test 编写测试

## 📖 参考资源

- [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html)
- [Room KMP](https://developer.android.com/kotlin/multiplatform/room)
- [Ktor Documentation](https://ktor.io/)
- [Koin Documentation](https://insert-koin.io/)
- [Arrow Documentation](https://arrow-kt.io/)
- [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

## 📄 许可证

MIT License

---

配置完成！开始构建你的 KMP 应用吧！🚀
