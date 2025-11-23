package com.minidog.dashboard.service;

import com.minidog.dashboard.model.LogEntry;
import com.minidog.dashboard.repository.LogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * =============================================================================
 * LogService.java - 日志业务逻辑服务
 * =============================================================================
 *
 * 【文件作用】
 * 封装日志处理的核心业务逻辑，包括：
 * - 日志解析和存储
 * - 统计数据计算
 * - 数据清理
 *
 * 【设计决策】为什么要有Service层？
 * - 分离业务逻辑和数据访问
 * - Controller负责HTTP，Service负责业务
 * - 业务逻辑可以被多个Controller复用
 * - 便于单元测试
 *
 * 【面试要点】
 * 问：Service层和Repository层的区别？
 * 答：
 * - Repository只做数据CRUD，不含业务逻辑
 * - Service组合多个Repository操作，实现业务逻辑
 * - Service可以有事务控制
 */
@Service
@Slf4j
public class LogService {

    /**
     * 【设计决策】日志格式的正则表达式
     *
     * 日志格式: [2024-01-01 10:00:00] [ERROR] [PaymentService] 消息内容
     *
     * 正则解释:
     * - \[(.*?)\]: 匹配方括号内的内容（非贪婪）
     * - (.*): 匹配剩余的消息内容
     *
     * 【面试要点】
     * 问：为什么用正则解析日志？
     * 答：
     * 1. 日志格式固定，正则高效
     * 2. 一次匹配提取所有字段
     * 3. 比split更准确（消息中可能包含分隔符）
     */
    private static final Pattern LOG_PATTERN = Pattern.compile(
            "\\[(.*?)\\]\\s*\\[(.*?)\\]\\s*\\[(.*?)\\]\\s*(.*)"
    );

    /**
     * 日期时间格式化器
     * 【注意】必须和LogSimulator生成的格式一致
     */
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private LogRepository logRepository;

    @Autowired
    private AlertService alertService;

    /**
     * 【函数说明】解析日志字符串并保存到数据库
     *
     * 【输入】logLine: String - 原始日志行
     *        例如: "[2024-01-01 10:00:00] [ERROR] [UserService] 认证失败"
     *
     * 【输出】LogEntry - 保存后的日志实体（包含ID）
     *        如果解析失败返回null
     *
     * 【处理流程】
     * 1. 用正则表达式解析日志
     * 2. 提取时间戳、级别、服务、消息
     * 3. 创建LogEntry对象
     * 4. 保存到数据库
     * 5. 如果是ERROR，触发告警检查
     *
     * 【异常处理】
     * - 格式不匹配：记录警告，返回null
     * - 日期解析失败：记录错误，返回null
     *
     * 【面试要点】
     * 问：为什么每次保存ERROR都检查告警？
     * 答：实现实时告警，不需要等定时任务
     */
    @Transactional
    public LogEntry parseAndSave(String logLine) {
        // 步骤1：正则匹配
        Matcher matcher = LOG_PATTERN.matcher(logLine.trim());

        if (!matcher.matches()) {
            // 格式不匹配，记录警告
            log.warn("日志格式不匹配，无法解析: {}", logLine);
            return null;
        }

        try {
            // 步骤2：提取各个字段
            // group(0)是整个匹配，group(1)开始是各个捕获组
            String timestampStr = matcher.group(1);
            String level = matcher.group(2);
            String service = matcher.group(3);
            String message = matcher.group(4);

            // 步骤3：解析时间戳
            LocalDateTime timestamp = LocalDateTime.parse(timestampStr, DATE_FORMATTER);

            // 步骤4：创建实体对象
            LogEntry entry = new LogEntry();
            entry.setTimestamp(timestamp);
            entry.setLevel(level);
            entry.setService(service);
            entry.setMessage(message);

            // 步骤5：保存到数据库
            LogEntry savedEntry = logRepository.save(entry);

            log.debug("日志保存成功: id={}, level={}, service={}",
                    savedEntry.getId(), level, service);

            // 步骤6：如果是ERROR，检查是否需要告警
            // 【设计决策】为什么在这里检查告警？
            // - 实时性：错误发生立即检查
            // - 不漏报：每个错误都会触发检查
            if ("ERROR".equals(level)) {
                alertService.checkAndAlert();
            }

            return savedEntry;

        } catch (Exception e) {
            log.error("解析日志失败: {}, 错误: {}", logLine, e.getMessage());
            return null;
        }
    }

    /**
     * 【函数说明】批量保存日志
     *
     * 【输入】logLines: List<String> - 日志行列表
     *
     * 【输出】int - 成功保存的数量
     *
     * 【设计决策】为什么需要批量保存？
     * - 提高性能，减少数据库交互
     * - 支持批量上报的场景
     *
     * 【面试要点】
     * 问：如何优化批量保存性能？
     * 答：
     * 1. 使用saveAll而不是逐个save
     * 2. 配置batch_size
     * 3. 关闭自动flush
     */
    @Transactional
    public int batchSave(List<String> logLines) {
        int successCount = 0;

        for (String line : logLines) {
            if (parseAndSave(line) != null) {
                successCount++;
            }
        }

        // 批量保存完成后统一检查告警
        alertService.checkAndAlert();

        log.info("批量保存完成: 总数={}, 成功={}", logLines.size(), successCount);
        return successCount;
    }

    /**
     * 【函数说明】获取指定时间范围的统计数据
     *
     * 【输入】minutes: int - 统计最近N分钟的数据
     *
     * 【输出】Map<String, Object> - 统计结果，包含：
     *        - total: 总数
     *        - errorCount: 错误数
     *        - warnCount: 警告数
     *        - infoCount: 信息数
     *        - errorRate: 错误率(%)
     *        - byService: 各服务错误统计
     *
     * 【使用场景】
     * - 仪表盘展示统计数据
     * - 告警判断
     */
    public Map<String, Object> getStatistics(int minutes) {
        // 计算时间范围
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusMinutes(minutes);

        // 查询各级别数量
        long total = logRepository.countByTimestampBetween(start, end);
        long errorCount = logRepository.countByLevelAndTimestampBetween("ERROR", start, end);
        long warnCount = logRepository.countByLevelAndTimestampBetween("WARN", start, end);
        long infoCount = logRepository.countByLevelAndTimestampBetween("INFO", start, end);

        // 计算错误率
        // 【注意】避免除零错误
        double errorRate = total > 0 ? (errorCount * 100.0 / total) : 0;

        // 查询各服务的错误统计
        List<Object[]> serviceErrors = logRepository.countErrorsByService(start, end);
        Map<String, Long> byService = new HashMap<>();
        for (Object[] row : serviceErrors) {
            byService.put((String) row[0], (Long) row[1]);
        }

        // 组装返回结果
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", total);
        stats.put("errorCount", errorCount);
        stats.put("warnCount", warnCount);
        stats.put("infoCount", infoCount);
        stats.put("errorRate", Math.round(errorRate * 100) / 100.0); // 保留两位小数
        stats.put("byService", byService);
        stats.put("timeWindowMinutes", minutes);
        stats.put("startTime", start.format(DATE_FORMATTER));
        stats.put("endTime", end.format(DATE_FORMATTER));

        return stats;
    }

    /**
     * 【函数说明】获取最近的日志列表
     *
     * 【输入】
     * - level: 日志级别（可选，null表示全部）
     * - limit: 返回数量限制
     *
     * 【输出】List<LogEntry> - 日志列表
     */
    public List<LogEntry> getRecentLogs(String level, int limit) {
        if (level != null && !level.isEmpty()) {
            return logRepository.findTop50ByLevelOrderByTimestampDesc(level);
        }
        return logRepository.findTop100ByOrderByTimestampDesc();
    }

    /**
     * 【函数说明】清理过期日志
     *
     * 【输入】hoursToKeep: int - 保留最近N小时的数据
     *
     * 【输出】无
     *
     * 【设计决策】为什么需要定期清理？
     * - H2是内存数据库，容量有限
     * - 历史数据价值递减
     * - 保持查询性能
     *
     * 【使用场景】
     * - 定时任务调用
     * - 手动清理
     */
    @Transactional
    public void cleanOldLogs(int hoursToKeep) {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(hoursToKeep);

        log.info("开始清理 {} 之前的日志", cutoff.format(DATE_FORMATTER));

        logRepository.deleteByTimestampBefore(cutoff);

        log.info("日志清理完成");
    }

    // =============================================================================
    // 新增功能：搜索、趋势、健康度评分、导出
    // =============================================================================

    /**
     * 【函数说明】搜索日志
     *
     * 【输入】
     * - keyword: 搜索关键词（可为空）
     * - level: 日志级别（可为空）
     * - service: 服务名（可为空）
     * - minutes: 时间范围（分钟）
     *
     * 【输出】List<LogEntry> - 符合条件的日志列表
     *
     * 【设计决策】支持多条件组合搜索
     * - 关键词搜索消息内容
     * - 级别过滤
     * - 服务过滤
     * - 时间范围限制
     */
    public List<LogEntry> searchLogs(String keyword, String level, String service, int minutes) {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusMinutes(minutes);

        // 处理空字符串为null，让Repository的动态查询生效
        keyword = (keyword != null && keyword.trim().isEmpty()) ? null : keyword;
        level = (level != null && level.trim().isEmpty()) ? null : level;
        service = (service != null && service.trim().isEmpty()) ? null : service;

        return logRepository.searchLogs(keyword, level, service, start, end);
    }

    /**
     * 【函数说明】获取所有服务列表
     *
     * 【输出】List<String> - 服务名列表
     *
     * 【使用场景】
     * - 前端下拉框选项
     */
    public List<String> getAllServices() {
        return logRepository.findAllServices();
    }

    /**
     * 【函数说明】获取历史趋势数据
     *
     * 【输入】hours: 最近N小时
     *
     * 【输出】Map<String, Object> - 趋势数据
     *        - labels: 时间标签数组
     *        - error: 错误数数组
     *        - warn: 警告数数组
     *        - info: 信息数数组
     *
     * 【设计决策】按小时聚合
     * - 粒度适中
     * - 便于绘制折线图
     *
     * 【面试要点】
     * 这个数据结构是专门为Chart.js设计的
     */
    public Map<String, Object> getTrendData(int hours) {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusHours(hours);

        List<Object[]> data = logRepository.countByHourAndLevel(start, end);

        // 构建小时到数据的映射
        Map<Integer, Map<String, Long>> hourData = new HashMap<>();

        for (Object[] row : data) {
            Integer hour = ((Number) row[0]).intValue();
            String level = (String) row[1];
            Long count = (Long) row[2];

            hourData.computeIfAbsent(hour, k -> new HashMap<>()).put(level, count);
        }

        // 构建结果数组
        List<String> labels = new ArrayList<>();
        List<Long> errorData = new ArrayList<>();
        List<Long> warnData = new ArrayList<>();
        List<Long> infoData = new ArrayList<>();

        // 填充所有小时的数据（0-23）
        for (int h = 0; h < 24; h++) {
            labels.add(String.format("%02d:00", h));
            Map<String, Long> counts = hourData.getOrDefault(h, new HashMap<>());
            errorData.add(counts.getOrDefault("ERROR", 0L));
            warnData.add(counts.getOrDefault("WARN", 0L));
            infoData.add(counts.getOrDefault("INFO", 0L));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("labels", labels);
        result.put("error", errorData);
        result.put("warn", warnData);
        result.put("info", infoData);

        return result;
    }

    /**
     * 【函数说明】计算各服务的健康度评分
     *
     * 【输入】minutes: 统计时间范围（分钟）
     *
     * 【输出】List<Map<String, Object>> - 每个服务的健康度信息
     *        - service: 服务名
     *        - score: 健康度评分（0-100）
     *        - total: 总日志数
     *        - errorCount: 错误数
     *        - warnCount: 警告数
     *        - errorRate: 错误率
     *
     * 【评分算法】
     * score = 100 - (errorRate * 2) - (warnRate * 0.5)
     * - 错误权重高（*2）
     * - 警告权重低（*0.5）
     * - 最低0分，最高100分
     *
     * 【面试要点】
     * 可以讨论不同的评分算法设计
     */
    public List<Map<String, Object>> getServiceHealth(int minutes) {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusMinutes(minutes);

        List<Object[]> data = logRepository.countByServiceAndLevel(start, end);

        // 按服务聚合数据
        Map<String, Map<String, Long>> serviceData = new HashMap<>();

        for (Object[] row : data) {
            String service = (String) row[0];
            String level = (String) row[1];
            Long count = (Long) row[2];

            serviceData.computeIfAbsent(service, k -> new HashMap<>()).put(level, count);
        }

        // 计算每个服务的健康度
        List<Map<String, Object>> result = new ArrayList<>();

        for (Map.Entry<String, Map<String, Long>> entry : serviceData.entrySet()) {
            String service = entry.getKey();
            Map<String, Long> counts = entry.getValue();

            long errorCount = counts.getOrDefault("ERROR", 0L);
            long warnCount = counts.getOrDefault("WARN", 0L);
            long infoCount = counts.getOrDefault("INFO", 0L);
            long total = errorCount + warnCount + infoCount;

            // 计算健康度评分
            double errorRate = total > 0 ? (errorCount * 100.0 / total) : 0;
            double warnRate = total > 0 ? (warnCount * 100.0 / total) : 0;

            // 评分公式：100 - 错误惩罚 - 警告惩罚
            double score = 100 - (errorRate * 2) - (warnRate * 0.5);
            score = Math.max(0, Math.min(100, score)); // 限制在0-100

            Map<String, Object> serviceHealth = new HashMap<>();
            serviceHealth.put("service", service);
            serviceHealth.put("score", Math.round(score * 10) / 10.0);
            serviceHealth.put("total", total);
            serviceHealth.put("errorCount", errorCount);
            serviceHealth.put("warnCount", warnCount);
            serviceHealth.put("infoCount", infoCount);
            serviceHealth.put("errorRate", Math.round(errorRate * 100) / 100.0);

            result.add(serviceHealth);
        }

        // 按分数排序（低分在前，需要关注）
        result.sort((a, b) -> Double.compare(
                (Double) a.get("score"),
                (Double) b.get("score")
        ));

        return result;
    }

    /**
     * 【函数说明】导出日志为CSV格式
     *
     * 【输入】minutes: 导出最近N分钟的数据
     *
     * 【输出】String - CSV格式的日志数据
     *
     * 【设计决策】为什么用CSV？
     * - 通用格式，Excel可以直接打开
     * - 文本格式，方便传输
     * - 结构简单，生成容易
     */
    public String exportLogsToCsv(int minutes) {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusMinutes(minutes);

        List<LogEntry> logs = logRepository.findAllForExport(start, end);

        StringBuilder csv = new StringBuilder();

        // CSV头
        csv.append("ID,时间戳,级别,服务,消息\n");

        // CSV内容
        for (LogEntry log : logs) {
            csv.append(log.getId()).append(",");
            csv.append(log.getTimestamp().format(DATE_FORMATTER)).append(",");
            csv.append(log.getLevel()).append(",");
            csv.append(log.getService()).append(",");
            // 消息中的逗号和换行需要处理
            String message = log.getMessage()
                    .replace("\"", "\"\"")
                    .replace("\n", " ");
            csv.append("\"").append(message).append("\"");
            csv.append("\n");
        }

        return csv.toString();
    }

    /**
     * 【函数说明】导出日志为JSON格式
     *
     * 【输入】minutes: 导出最近N分钟的数据
     *
     * 【输出】List<LogEntry> - 日志列表（可直接序列化为JSON）
     */
    public List<LogEntry> exportLogsToJson(int minutes) {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusMinutes(minutes);
        return logRepository.findAllForExport(start, end);
    }
}
