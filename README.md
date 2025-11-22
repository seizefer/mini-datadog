# 🐕 Mini-Datadog 日志监控系统

一个简单但完整的日志监控系统，用于学习和面试展示。

## 🎯 项目简介

Mini-Datadog 是一个迷你版的日志监控系统，实现了从日志生成、采集、存储到可视化展示的完整链路。

### 系统架构

```
┌─────────────┐      ┌─────────────┐      ┌─────────────┐      ┌─────────────┐
│   Python    │      │    Java     │      │ Spring Boot │      │   Browser   │
│  Simulator  │ ──▶  │  LogAgent   │ ──▶  │   Server    │ ──▶  │  Dashboard  │
│  生成日志    │ 文件  │  采集日志    │ HTTP │  存储处理    │ HTTP │  可视化展示   │
└─────────────┘      └─────────────┘      └─────────────┘      └─────────────┘
```

### 技术栈

- **后端**: Java 11 + Spring Boot 2.7
- **数据库**: H2 (内存数据库)
- **前端**: HTML + JavaScript + Chart.js
- **日志生成**: Python 3
- **日志采集**: Java + OkHttp

---

## 🚀 快速启动 (傻瓜式教程)

### 前置要求

- Java 11+
- Maven 3.6+
- Python 3.6+

### 第一步：启动 Dashboard Server

```bash
# 进入服务端目录
cd dashboard-server

# 编译并启动（首次需要下载依赖，可能需要几分钟）
mvn spring-boot:run
```

看到以下输出表示启动成功：
```
==============================================
  Mini-Datadog Dashboard Server 启动成功！
  访问地址: http://localhost:8080
  H2控制台: http://localhost:8080/h2-console
==============================================
```

### 第二步：启动日志模拟器

打开新的终端窗口：

```bash
# 进入模拟器目录
cd log-simulator

# 运行Python脚本
python LogSimulator.py
```

看到日志开始生成：
```
🚀 日志模拟器启动
📁 输出文件: ./logs/app.log
[1] [2024-01-01 10:00:00] [INFO] [UserService] 用户登录成功...
[2] [2024-01-01 10:00:01] [ERROR] [PaymentService] 支付处理异常...
```

### 第三步：启动 LogAgent

打开新的终端窗口：

```bash
# 进入Agent目录
cd log-agent

# 编译
mvn clean package -DskipTests

# 运行（监控模拟器生成的日志文件）
java -jar target/log-agent-1.0.0.jar ../log-simulator/logs/app.log
```

看到日志开始采集：
```
==============================================
  Mini-Datadog Log Agent 启动
  监控文件: ../log-simulator/logs/app.log
==============================================
[读取] [2024-01-01 10:00:00] [INFO] [UserService] ...
[发送成功] 10 条日志
```

### 第四步：查看仪表盘

打开浏览器访问：**http://localhost:8080**

你将看到：
- 实时错误率统计
- 日志级别分布图
- 各服务错误排行
- 实时日志列表

---

## 📁 项目结构

```
mini-datadog/
├── log-simulator/           # 日志模拟器
│   └── LogSimulator.py      # Python脚本
│
├── log-agent/               # 日志采集代理
│   ├── pom.xml
│   └── src/.../LogAgent.java
│
├── dashboard-server/        # 仪表盘服务端
│   ├── pom.xml
│   └── src/
│       ├── controller/DashboardController.java
│       ├── service/
│       │   ├── LogService.java
│       │   └── AlertService.java
│       ├── model/LogEntry.java
│       ├── repository/LogRepository.java
│       └── resources/
│           ├── application.properties
│           └── static/index.html
│
├── README.md                # 本文件
├── 项目复盘报告.md           # 技术复盘
└── 面试准备文档.md           # 面试Q&A
```

---

## 🔧 API 接口

### 日志接收

```bash
# 单条日志
POST /api/logs
{
  "log": "[2024-01-01 10:00:00] [ERROR] [UserService] 错误消息"
}

# 批量日志
POST /api/logs/batch
{
  "logs": ["日志1", "日志2", ...]
}
```

### 查询统计

```bash
# 获取统计数据
GET /api/stats?minutes=5

# 获取日志列表
GET /api/logs?level=ERROR&limit=50

# 获取告警历史
GET /api/alerts?limit=20
```

### 运维操作

```bash
# 健康检查
GET /api/health

# 手动触发告警检查
POST /api/alerts/check

# 清理旧日志
DELETE /api/logs?hoursToKeep=24
```

---

## ⚙️ 配置说明

### 告警阈值配置

编辑 `dashboard-server/src/main/resources/application.properties`:

```properties
# 错误率告警阈值（百分比）
alert.error-rate-threshold=10

# 统计时间窗口（分钟）
alert.time-window-minutes=5
```

### 日志模拟器参数

```bash
python LogSimulator.py --help

# 常用参数
-o, --output    日志输出文件路径
-i, --interval  生成间隔（秒）
-d, --duration  运行时长（秒）

# 示例
python LogSimulator.py -o /tmp/app.log -i 0.5 -d 300
```

---

## 🐛 常见问题

### Q1: Maven下载依赖很慢？

配置阿里云镜像，编辑 `~/.m2/settings.xml`:

```xml
<mirrors>
  <mirror>
    <id>aliyun</id>
    <mirrorOf>central</mirrorOf>
    <url>https://maven.aliyun.com/repository/central</url>
  </mirror>
</mirrors>
```

### Q2: 端口8080被占用？

修改 `application.properties`:
```properties
server.port=8081
```

### Q3: LogAgent连接不上Server？

确保Server已启动，检查URL是否正确：
```bash
java -jar log-agent.jar ./logs/app.log http://localhost:8080/api/logs/batch
```

### Q4: 看不到图表数据？

1. 确认日志模拟器在运行
2. 确认LogAgent在运行
3. 打开浏览器开发者工具查看网络请求
4. 检查Server日志是否有错误

---

## 📚 学习要点

1. **日志格式设计**: 标准化格式便于解析
2. **文件监控**: 增量读取 vs WatchService
3. **批量处理**: 提高吞吐量
4. **REST API**: 前后端分离
5. **定时任务**: Spring Scheduling
6. **告警策略**: 阈值 + 防抖动
7. **数据可视化**: Chart.js

---

## 📝 License

MIT License - 随便用，求Star ⭐
