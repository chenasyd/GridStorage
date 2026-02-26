# GridStorage

一个功能完整的 Minecraft 网格存储插件，支持 Spigot 和 Folia。

## 功能特性

- **54格大型存储空间**: 每个仓库槽位提供54格的独立存储空间
- **网格化GUI设计**: 简洁直观的网格界面
- **多页浏览系统**: 支持最多100个仓库槽位，通过翻页轻松管理
- **独立存储**: 每个玩家拥有独立的仓库空间
- **实时更新**: GUI更新仅影响当前操作玩家，避免物品被篡改
- **持久化存储**: 使用 SQLite 数据库存储物品数据，支持 NBT 完整序列化
- **自动保存**: 每5分钟自动保存所有玩家数据
- **多语言支持**: 支持中文和英文界面

## 命令使用

### 玩家命令
- `/gridstorage` - 打开网格仓库主界面
- `/gridstorage help` - 显示帮助信息
- `/gridstorage open [槽位号]` - 直接打开指定槽位

### 管理员命令
- `/gridstorageadmin reload` - 重新加载配置
- `/gridstorageadmin help` - 显示管理员帮助

## 权限系统

| 权限节点 | 描述 | 默认 |
|---------|------|------|
| `gridstorage.use` | 允许使用网格存储系统 | true |
| `gridstorage.admin` | 网格存储管理员权限 | op |
| `gridstorage.create` | 允许创建网格 | true |
| `gridstorage.access` | 允许访问网格 | true |
| `gridstorage.delete` | 允许删除网格 | op |
| `gridstorage.invite` | 允许邀请玩家访问网格 | true |
| `gridstorage.kick` | 允许踢出网格访问者 | true |
| `gridstorage.administrate` | 允许管理网格权限 | op |
| `gridstorage.economy` | 允许管理网格经济 | true |

## GUI布局

### 主网格GUI (54格)
- **槽位1-45**: 仓库槽位按钮（点击打开对应仓库）
- **槽位46**: 前一页按钮
- **槽位47-53**: 玻璃板装饰
- **槽位54**: 后一页按钮

### 单个仓库GUI (54格)
- **槽位1-54**: 可用的存储空间

## 配置文件

### config.yml
```yaml
# 数据库配置
database:
  # 数据存储类型: sqlite(本地文件) 或 mysql(数据库)
  type: sqlite

# 网格配置
grid:
  # 网格名称
  name: "网格仓库"
  # 最大仓库槽位数量
  max-storage-count: 100
  # 每页显示的槽位数量
  page-size: 45
  # GUI大小
  gui-size: 54

# 语言配置
language:
  # 默认语言: zh(中文), en(英文)
  default: "zh"
  # 是否允许玩家切换语言
  allow-player-switch: false

# 消息配置
messages:
  # 是否启用消息
  enabled: true
  # 消息前缀
  prefix: "&6[网格存储] &r"
  # 消息颜色
  colors:
    success: "&a"
    error: "&c"
    info: "&e"
    warning: "&6"

# 日志配置
logging:
  # 是否启用日志
  enabled: true
  # 日志级别: DEBUG, INFO, WARN, ERROR
  level: INFO
  # 是否记录到文件
  file-logging: true
  # 日志文件路径
  log-file: "logs/gridstorage.log"
  # 最大日志文件大小（MB）
  max-file-size: 10
  # 保留的日志文件数量
  max-files: 5
```

### messages_zh.yml (中文消息)
```yaml
storage:
  gui:
    title: "&6网格仓库 &7- 第 &e{0}&7/&e{1} &7页"
    slot-title: "&6仓库 #{0}"
    slot-lore:
      - "&7点击打开此仓库"
      - "&7容量: 54 格"
```

### messages_en.yml (英文消息)
```yaml
storage:
  gui:
    title: "&6Grid Storage &7- Page &e{0}&7/&e{1}"
    slot-title: "&6Storage #{0}"
    slot-lore:
      - "&7Click to open this storage"
      - "&7Capacity: 54 slots"
```

## 开发说明

### 项目结构
```
src/main/java/com/gridstorage/
├── GridStorage.java          # 主类
├── config/
│   └── ConfigManager.java   # 配置管理器
├── model/
│   ├── StorageSlot.java      # 槽位数据模型
│   └── PlayerStorage.java    # 玩家仓库模型
├── manager/
│   ├── GUIManager.java       # GUI管理器
│   └── StorageManager.java  # 存储管理器
├── database/
│   └── DatabaseManager.java  # 数据库管理器
├── command/
│   └── GridStorageCommand.java # 命令处理器
└── listener/
    └── GUIListener.java     # GUI事件监听器
```

### 依赖
- Spigot API 1.21+
- NBT-API 2.15.5+
- SQLite (内置)

### 构建项目
```bash
mvn clean package
```

构建后的 JAR 文件位于 `target/` 目录。

### 数据库设计

#### 表结构

**player_storage 表**
```sql
CREATE TABLE player_storage (
    player_uuid TEXT PRIMARY KEY,
    current_page INTEGER NOT NULL DEFAULT 0,
    max_pages INTEGER NOT NULL DEFAULT 1
);
```

**storage_slots 表**
```sql
CREATE TABLE storage_slots (
    player_uuid TEXT NOT NULL,
    slot_id INTEGER NOT NULL,
    items_nbt TEXT NOT NULL,
    PRIMARY KEY (player_uuid, slot_id),
    FOREIGN KEY (player_uuid) REFERENCES player_storage(player_uuid)
);
```

### NBT 序列化说明

插件使用 NBT-API 完整序列化物品数据，包括物品 ID、数量、附魔、自定义 NBT 标签等。

**序列化方法**:
```java
// 将物品序列化为 NBT
ReadWriteNBT itemNbt = NBT.itemStackToNBT(item);

// 将 NBT 反序列化为物品
ItemStack item = NBT.itemStackFromNBT(itemNbt);
```

### 自动保存机制

- **自动保存**: 每 5 分钟（300秒）自动保存所有玩家数据
- **GUI关闭保存**: 当玩家关闭仓库 GUI 时自动保存当前槽位内容
- **服务器关闭保存**: 服务器关闭时强制保存所有数据

## 技术实现

### 线程安全

- 数据库操作在异步线程执行，避免阻塞主线程
- Folia 区域化调度支持
- 使用读写锁保护并发访问

### 数据一致性

- 使用 SQLite WAL 模式提高并发性能
- GUI 关闭事件确保数据同步
- 服务器优雅关闭时保存所有数据

## 常见问题

### Q: 数据会丢失吗？
A: 不会。插件使用 SQLite 数据库持久化存储，并有多重保护机制：
- 自动保存（每5分钟）
- GUI关闭时保存
- 服务器关闭时保存

### Q: 支持哪些物品？
A: 支持所有 Minecraft 物品，包括：
- 基础物品和方块
- 附魔物品
- 自定义 NBT 标签物品
- 药水、旗帜等复杂物品

### Q: 如何备份仓库数据？
A: 仓库数据存储在 `plugins/GridStorage/database/` 目录下的 SQLite 数据库文件中，直接复制该文件即可备份。

## 许可证

GPLv3 License

## 作者

chenasyd

## 版本历史

### v1.0.0
- 初始版本
- 实现基础GUI框架
- 实现仓库槽位管理
- 实现翻页功能
- 实现 SQLite 数据库持久化
- 实现完整 NBT 序列化
- 实现自动保存机制
- 实现多语言支持

## 相关链接

- [NBT-API 文档](https://github.com/tr7zw/Item-NBT-API)
- [Spigot API 文档](https://hub.spigotmc.org/javadocs/spigot/)
