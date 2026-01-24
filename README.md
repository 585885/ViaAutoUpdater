# ViaAutoUpdater

[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://openjdk.org/projects/jdk/17/)
[![Paper](https://img.shields.io/badge/Paper-1.21.11-green.svg)](https://papermc.io/)
[![Folia](https://img.shields.io/badge/Folia-Supported-brightgreen.svg)](https://papermc.io/software/folia)

一个用于自动更新 Via 系列插件的 Minecraft 服务器插件，支持 Paper 和 Folia 服务器。
**注意**：本插件由 AI 开发

## 功能特性

- ✅ **自动检查更新** - 可配置的定时检查 Via 系列插件更新
- ✅ **一键下载更新** - 自动下载最新版本到 `update` 文件夹
- ✅ **Paper Update 机制** - 利用 Paper 的 update 功能，无需停机更新
- ✅ **管理员通知** - 向 OP 玩家发送更新通知
- ✅ **多插件支持** - 同时支持 ViaVersion、ViaBackwards、ViaRewind、ViaAprilFools
- ✅ **自动备份** - 更新前自动备份当前版本
- ✅ **重启提醒** - 更新完成后提醒重启服务器
- ✅ **全中文界面** - 所有消息均为中文
- ✅ **Folia 兼容** - 完美支持 Folia 多线程服务器

## 支持的插件

- ViaVersion
- ViaBackwards
- ViaRewind
- ViaAprilFools

## 安装

1. 从 [Releases](https://github.com/585885/ViaAutoUpdater/releases) 下载最新版本的 JAR 文件
2. 将 JAR 文件放入服务器的 `plugins` 文件夹
3. 重启服务器
4. 配置文件会自动生成

## 配置

插件会在 `plugins/ViaAutoUpdater/` 目录下生成以下配置文件：

### config.yml

```yaml
# 是否启用自动检查更新
auto-check: true

# 自动检查更新间隔（分钟）
check-interval: 60

# 要检查和更新的 Via 插件列表
plugins:
  - ViaVersion
  - ViaBackwards
  - ViaRewind
  - ViaAprilFools

# 是否在下载前备份当前插件
backup: true

# 备份文件夹路径（相对于插件目录）
backup-folder: "ViaUpdateBackups"

# 是否在更新完成后通知管理员
notify-admins: true

# 是否在更新完成后提醒重启服务器
restart-reminder: true

# 是否在服务器启动时检查更新
check-on-startup: true

# GitHub API 配置
github:
  api-url: "https://api.github.com"
  timeout: 30
```

### messages_zh_CN.yml

自定义中文消息显示内容。

## 使用方法

### 命令

所有命令都需要管理员权限 (`viaupdate.admin`)

```bash
# 检查所有插件的更新
/viaupdate check

# 检查指定插件的更新
/viaupdate check ViaVersion

# 更新所有插件
/viaupdate update

# 更新指定插件
/viaupdate update ViaVersion

# 重载配置文件
/viaupdate reload

# 显示帮助信息
/viaupdate help
```

### 权限

| 权限 | 描述 | 默认 |
|------|------|------|
| `viaupdate.admin` | 使用所有 ViaUpdate 命令 | OP |
| `viaupdate.notify` | 接收更新通知 | OP |
| `viaupdate.check` | 检查更新 | OP |
| `viaupdate.update` | 下载更新 | OP |
| `viaupdate.reload` | 重载配置 | OP |

## 工作流程

1. 插件从 GitHub API 获取 Via 插件的最新版本信息
2. 检测到更新时，向管理员发送通知
3. 执行更新命令时，自动备份当前版本（如果启用）
4. 下载最新版本到 `plugins/update` 文件夹
5. 更新完成后提醒管理员重启服务器
6. 重启后 Paper 会自动应用 `update` 文件夹中的插件

## 构建

### 前置要求

- Java 17 或更高版本
- Maven 3.6 或更高版本

### 构建步骤

```bash
# 克隆仓库
git clone https://github.com/585885/ViaAutoUpdater.git
cd ViaAutoUpdater

# 编译项目
mvn clean package

# 编译后的 JAR 文件位于 target/ViaAutoUpdater-1.0.0.jar
```

## 依赖

- [Paper API](https://papermc.io/) - 1.21.11-R0.1-SNAPSHOT
- [Gson](https://github.com/google/gson) - 2.10.1

## 兼容性

| 服务器版本 | 支持状态 |
|-----------|---------|
| Paper 1.21.11 | ✅ 完全支持 |
| Folia 1.21.11 | ✅ 完全支持 |
| Paper 1.20.x | ✅ 完全支持 |
| Folia 1.20.x | ✅ 完全支持 |
| Spigot | ⚠️ 部分支持 |

## 常见问题

### Q: 插件会自动更新吗？

A: 插件只会检查更新并下载到 `update` 文件夹，需要重启服务器才能应用更新。这是为了确保服务器稳定性。

### Q: 如何禁用自动检查更新？

A: 在 `config.yml` 中将 `auto-check` 设置为 `false`。

### Q: 备份的插件保存在哪里？

A: 默认保存在 `plugins/ViaUpdateBackups/` 文件夹中，文件名包含时间戳。

### Q: 支持自定义更新源吗？

A: 目前仅支持从 GitHub Releases 获取更新。

## 贡献

欢迎提交 Issue 和 Pull Request！

1. Fork 本仓库
2. 创建你的特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交你的更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启一个 Pull Request

## 许可证

本项目采用 MIT 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情。

## 致谢

- [ViaVersion](https://github.com/ViaVersion/ViaVersion) - Via 系列插件
- [PaperMC](https://papermc.io/) - Paper 服务器端
- [Folia](https://papermc.io/software/folia) - 多线程服务器端

## 联系方式

- GitHub Issues: [提交问题](https://github.com/585885/ViaAutoUpdater/issues)

## 更新日志

### v1.0.0 (2026-01-24)

- ✨ 初始发布
- ✅ 支持自动检查 Via 插件更新
- ✅ 支持下载更新到 update 文件夹
- ✅ 支持 Paper 和 Folia 服务器
- ✅ 支持自动备份
- ✅ 全中文界面

---

**注意**: 本插件仅用于教育目的。使用本插件时请遵守相关服务条款和开源协议。