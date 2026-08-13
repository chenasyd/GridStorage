# GridStorage

个人多页网格仓库插件，兼容 **Spigot / Paper / Folia 1.20+**。

## 功能

- 每位玩家独立仓库，最多可配置 N 个槽位（默认 100）
- 每个槽位 54 格，NBT 完整序列化物品
- 网格 GUI 翻页浏览；SQLite 持久化；约每 5 分钟自动保存
- Folia：区域/实体调度（不回退到 Folia 禁用的 BukkitScheduler）
- **NBTAPI**：`softdepend`，未安装时插件可加载但仓库功能关闭并提示

## 非目标（本插件不提供）

共享网格、邀请、Vault 经济、MySQL、PlaceholderAPI。

## 命令

| 命令 | 说明 |
|------|------|
| `/gridstorage` | 打开网格仓库 |
| `/gridstorage open [槽位]` | 打开指定槽位 |
| `/gridstorage help` | 帮助 |
| `/gridstorageadmin reload` | 重载配置（需 `gridstorage.admin`） |

## 权限

| 权限 | 默认 | 说明 |
|------|------|------|
| `gridstorage.use` | true | 使用仓库 |
| `gridstorage.admin` | op | 管理命令 |

## 依赖

- 服务端：Spigot / Paper / Folia **1.20+**
- 可选（功能必需）：[NBTAPI](https://www.spigotmc.org/resources/nbt-api.7939/)

## 配置要点

`config.yml`：

```yaml
grid:
  max-storage-count: 100   # 个人槽位数量上限
  page-size: 45
  gui-size: 54
```

数据文件：`plugins/GridStorage/gridstorage.db`（SQLite）。

## 构建

```bash
cd Plugins/GridStorage
mvn -q clean package
```

产物：`target/gridstorage-plugin-1.0.2.jar`
