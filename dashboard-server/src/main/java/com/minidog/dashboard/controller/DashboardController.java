package com.minidog.dashboard.controller;

import com.minidog.dashboard.model.LogEntry;
import com.minidog.dashboard.service.AlertService;
import com.minidog.dashboard.service.LogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * =============================================================================
 * DashboardController.java - 仪表盘REST API控制器
 * =============================================================================
 *
 * 【文件作用】
 * 提供所有的REST API接口，包括：
 * - 接收日志数据
 * - 查询统计信息
 * - 获取日志列表
 * - 查询告警状态
 *
 * 【设计决策】为什么用REST API？
 * - 标准的HTTP接口，任何语言都能调用
 * - 无状态，易于扩展
 * - 前后端分离
 *
 * 【API设计原则】
 * - 资源命名用名词复数：/api/logs
 * - 用HTTP方法表示操作：GET查询、POST创建
 * - 返回合适的状态码：200成功、400参数错误、500服务器错误
 *
 * 【面试要点】
 * 问：RESTful API有哪些最佳实践？
 * 答：
 * 1. 资源命名用名词
 * 2. 用HTTP方法表示动作
 * 3. 版本控制（/api/v1/）
 * 4. 合理使用状态码
 * 5. 支持分页和过滤
 */
@RestController
@RequestMapping("/api")
@Slf4j
@CrossOrigin(origins = "*") // 允许跨域，方便前端调试
public class DashboardController {

    @Autowired
    private LogService logService;

    @Autowired
    private AlertService alertService;

    /**
     * 【API】健康检查接口
     *
     * 【请求】GET /api/health
     *
     * 【响应】
     * {
     *   "status": "UP",
     *   "message": "Mini-Datadog is running"
     * }
     *
     * 【使用场景】
     * - 负载均衡器健康检查
     * - 监控系统探活
     * - 部署验证
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("message", "Mini-Datadog is running");
        return ResponseEntity.ok(response);
    }

    /**
     * 【API】接收单条日志
     *
     * 【请求】POST /api/logs
     * 【请求体】{ "log": "[2024-01-01 10:00:00] [ERROR] [UserService] 错误消息" }
     *
     * 【响应】
     * 成功：{ "success": true, "id": 123 }
     * 失败：{ "success": false, "error": "解析失败" }
     *
     * 【设计决策】为什么接收原始日志字符串？
     * - LogAgent负责采集，不负责解析
     * - 解析逻辑集中在Server端，易于修改
     * - 降低Agent的复杂度
     */
    @PostMapping("/logs")
    public ResponseEntity<Map<String, Object>> receiveLog(@RequestBody Map<String, String> payload) {
        Map<String, Object> response = new HashMap<>();

        // 获取日志内容
        String logLine = payload.get("log");

        if (logLine == null || logLine.trim().isEmpty()) {
            response.put("success", false);
            response.put("error", "日志内容不能为空");
            return ResponseEntity.badRequest().body(response);
        }

        // 解析并保存日志
        LogEntry entry = logService.parseAndSave(logLine);

        if (entry != null) {
            response.put("success", true);
            response.put("id", entry.getId());
            log.debug("接收日志成功: id={}", entry.getId());
            return ResponseEntity.ok(response);
        } else {
            response.put("success", false);
            response.put("error", "日志解析失败");
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 【API】批量接收日志
     *
     * 【请求】POST /api/logs/batch
     * 【请求体】{ "logs": ["日志1", "日志2", ...] }
     *
     * 【响应】
     * {
     *   "success": true,
     *   "received": 10,
     *   "saved": 9
     * }
     *
     * 【设计决策】为什么需要批量接口？
     * - 减少网络开销
     * - 提高吞吐量
     * - 适合高并发场景
     */
    @PostMapping("/logs/batch")
    public ResponseEntity<Map<String, Object>> receiveBatchLogs(
            @RequestBody Map<String, List<String>> payload) {

        Map<String, Object> response = new HashMap<>();

        List<String> logs = payload.get("logs");

        if (logs == null || logs.isEmpty()) {
            response.put("success", false);
            response.put("error", "日志列表不能为空");
            return ResponseEntity.badRequest().body(response);
        }

        // 批量保存
        int savedCount = logService.batchSave(logs);

        response.put("success", true);
        response.put("received", logs.size());
        response.put("saved", savedCount);

        log.info("批量接收日志: 收到={}, 保存={}", logs.size(), savedCount);

        return ResponseEntity.ok(response);
    }

    /**
     * 【API】获取统计数据
     *
     * 【请求】GET /api/stats?minutes=5
     *
     * 【响应】
     * {
     *   "total": 100,
     *   "errorCount": 10,
     *   "warnCount": 30,
     *   "infoCount": 60,
     *   "errorRate": 10.0,
     *   "byService": {
     *     "UserService": 5,
     *     "PaymentService": 3
     *   },
     *   "isAlerting": false,
     *   "threshold": 10.0
     * }
     *
     * 【使用场景】
     * - 仪表盘数据展示
     * - 实时刷新
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStatistics(
            @RequestParam(defaultValue = "5") int minutes) {

        // 获取基础统计数据
        Map<String, Object> stats = logService.getStatistics(minutes);

        // 添加告警状态
        stats.put("isAlerting", alertService.isCurrentlyAlerting());
        stats.put("threshold", alertService.getThreshold());

        return ResponseEntity.ok(stats);
    }

    /**
     * 【API】获取最近日志列表
     *
     * 【请求】GET /api/logs?level=ERROR&limit=50
     *
     * 【响应】
     * [
     *   {
     *     "id": 1,
     *     "timestamp": "2024-01-01T10:00:00",
     *     "level": "ERROR",
     *     "service": "UserService",
     *     "message": "认证失败"
     *   },
     *   ...
     * ]
     *
     * 【参数说明】
     * - level: 可选，过滤日志级别
     * - limit: 可选，返回数量限制
     */
    @GetMapping("/logs")
    public ResponseEntity<List<LogEntry>> getLogs(
            @RequestParam(required = false) String level,
            @RequestParam(defaultValue = "100") int limit) {

        List<LogEntry> logs = logService.getRecentLogs(level, limit);
        return ResponseEntity.ok(logs);
    }

    /**
     * 【API】获取告警历史
     *
     * 【请求】GET /api/alerts?limit=20
     *
     * 【响应】
     * [
     *   {
     *     "timestamp": "2024-01-01T10:00:00",
     *     "type": "ALERT",
     *     "errorRate": 15.5,
     *     "message": "错误率告警..."
     *   },
     *   ...
     * ]
     */
    @GetMapping("/alerts")
    public ResponseEntity<List<AlertService.AlertRecord>> getAlertHistory(
            @RequestParam(defaultValue = "20") int limit) {

        List<AlertService.AlertRecord> history = alertService.getAlertHistory(limit);
        return ResponseEntity.ok(history);
    }

    /**
     * 【API】手动触发告警检查
     *
     * 【请求】POST /api/alerts/check
     *
     * 【响应】
     * {
     *   "checked": true,
     *   "isAlerting": false
     * }
     *
     * 【使用场景】
     * - 调试告警功能
     * - 手动触发检查
     */
    @PostMapping("/alerts/check")
    public ResponseEntity<Map<String, Object>> triggerAlertCheck() {
        alertService.checkAndAlert();

        Map<String, Object> response = new HashMap<>();
        response.put("checked", true);
        response.put("isAlerting", alertService.isCurrentlyAlerting());

        return ResponseEntity.ok(response);
    }

    /**
     * 【API】清理旧日志
     *
     * 【请求】DELETE /api/logs?hoursToKeep=24
     *
     * 【响应】
     * {
     *   "success": true,
     *   "message": "清理完成"
     * }
     *
     * 【使用场景】
     * - 手动清理数据
     * - 释放内存
     */
    @DeleteMapping("/logs")
    public ResponseEntity<Map<String, Object>> cleanLogs(
            @RequestParam(defaultValue = "24") int hoursToKeep) {

        logService.cleanOldLogs(hoursToKeep);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "清理完成，保留最近" + hoursToKeep + "小时的数据");

        return ResponseEntity.ok(response);
    }

    // =============================================================================
    // 新增API：搜索、趋势、健康度、导出
    // =============================================================================

    /**
     * 【API】搜索日志
     *
     * 【请求】GET /api/logs/search?keyword=支付&level=ERROR&service=PaymentService&minutes=60
     *
     * 【响应】日志列表
     *
     * 【参数说明】
     * - keyword: 搜索关键词（可选）
     * - level: 日志级别（可选）
     * - service: 服务名（可选）
     * - minutes: 时间范围（默认60分钟）
     */
    @GetMapping("/logs/search")
    public ResponseEntity<List<LogEntry>> searchLogs(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String service,
            @RequestParam(defaultValue = "60") int minutes) {

        List<LogEntry> logs = logService.searchLogs(keyword, level, service, minutes);
        return ResponseEntity.ok(logs);
    }

    /**
     * 【API】获取所有服务列表
     *
     * 【请求】GET /api/services
     *
     * 【响应】
     * ["UserService", "PaymentService", ...]
     *
     * 【使用场景】
     * - 前端搜索框的服务下拉选项
     */
    @GetMapping("/services")
    public ResponseEntity<List<String>> getAllServices() {
        List<String> services = logService.getAllServices();
        return ResponseEntity.ok(services);
    }

    /**
     * 【API】获取历史趋势数据
     *
     * 【请求】GET /api/trend?hours=24
     *
     * 【响应】
     * {
     *   "labels": ["00:00", "01:00", ...],
     *   "error": [5, 3, 8, ...],
     *   "warn": [10, 15, 12, ...],
     *   "info": [100, 120, 95, ...]
     * }
     *
     * 【使用场景】
     * - 绘制趋势折线图
     */
    @GetMapping("/trend")
    public ResponseEntity<Map<String, Object>> getTrendData(
            @RequestParam(defaultValue = "24") int hours) {

        Map<String, Object> trend = logService.getTrendData(hours);
        return ResponseEntity.ok(trend);
    }

    /**
     * 【API】获取服务健康度评分
     *
     * 【请求】GET /api/health/services?minutes=30
     *
     * 【响应】
     * [
     *   {
     *     "service": "PaymentService",
     *     "score": 75.5,
     *     "total": 100,
     *     "errorCount": 10,
     *     "warnCount": 20,
     *     "errorRate": 10.0
     *   },
     *   ...
     * ]
     *
     * 【使用场景】
     * - 服务健康度排行榜
     * - 问题服务定位
     */
    @GetMapping("/health/services")
    public ResponseEntity<List<Map<String, Object>>> getServiceHealth(
            @RequestParam(defaultValue = "30") int minutes) {

        List<Map<String, Object>> health = logService.getServiceHealth(minutes);
        return ResponseEntity.ok(health);
    }

    /**
     * 【API】导出日志为CSV
     *
     * 【请求】GET /api/export/csv?minutes=60
     *
     * 【响应】CSV文件下载
     *
     * 【使用场景】
     * - 下载日志进行离线分析
     * - 报告生成
     */
    @GetMapping("/export/csv")
    public ResponseEntity<String> exportToCsv(
            @RequestParam(defaultValue = "60") int minutes) {

        String csv = logService.exportLogsToCsv(minutes);

        return ResponseEntity.ok()
                .header("Content-Type", "text/csv; charset=UTF-8")
                .header("Content-Disposition", "attachment; filename=logs.csv")
                .body(csv);
    }

    /**
     * 【API】导出日志为JSON
     *
     * 【请求】GET /api/export/json?minutes=60
     *
     * 【响应】JSON文件下载
     */
    @GetMapping("/export/json")
    public ResponseEntity<List<LogEntry>> exportToJson(
            @RequestParam(defaultValue = "60") int minutes) {

        List<LogEntry> logs = logService.exportLogsToJson(minutes);

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=logs.json")
                .body(logs);
    }
}
