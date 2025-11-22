package com.minidog.dashboard.service;

import com.minidog.dashboard.repository.LogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * =============================================================================
 * AlertService.java - 告警服务
 * =============================================================================
 *
 * 【文件作用】
 * 监控错误率，当超过阈值时触发告警。
 * 这是监控系统的核心功能之一。
 *
 * 【设计决策】告警策略
 * 1. 阈值告警：错误率超过X%时告警
 * 2. 防抖动：短时间内不重复告警
 * 3. 恢复通知：错误率下降后通知恢复
 *
 * 【设计决策】为什么要防抖动？
 * - 避免告警风暴
 * - 错误可能是瞬时的
 * - 减少告警疲劳
 *
 * 【面试要点】
 * 问：真实的监控系统告警怎么设计？
 * 答：
 * 1. 多级别阈值（警告、严重、致命）
 * 2. 告警聚合和去重
 * 3. 告警升级和降级
 * 4. 多渠道通知（邮件、短信、钉钉）
 * 5. 告警确认和静默
 */
@Service
@Slf4j
public class AlertService {

    @Autowired
    private LogRepository logRepository;

    /**
     * 错误率阈值（从配置文件读取）
     * 【配置项】alert.error-rate-threshold
     */
    @Value("${alert.error-rate-threshold:10}")
    private double errorRateThreshold;

    /**
     * 统计时间窗口（分钟）
     * 【配置项】alert.time-window-minutes
     */
    @Value("${alert.time-window-minutes:5}")
    private int timeWindowMinutes;

    /**
     * 【设计决策】告警状态标记
     *
     * 为什么需要这个状态？
     * - 实现防抖动：告警中就不再重复告警
     * - 支持恢复通知：从告警状态恢复时通知
     */
    private volatile boolean isAlerting = false;

    /**
     * 【设计决策】上次告警时间
     *
     * 用于实现告警冷却时间，避免频繁告警
     */
    private LocalDateTime lastAlertTime;

    /**
     * 告警冷却时间（秒）
     * 在冷却时间内不会重复告警
     */
    private static final int ALERT_COOLDOWN_SECONDS = 60;

    /**
     * 【设计决策】告警历史记录
     *
     * 为什么用CopyOnWriteArrayList？
     * - 线程安全
     * - 读多写少的场景性能好
     * - 避免并发问题
     */
    private final List<AlertRecord> alertHistory = new CopyOnWriteArrayList<>();

    /**
     * 日期格式化器
     */
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 【函数说明】检查当前错误率并决定是否告警
     *
     * 【输入】无
     *
     * 【输出】无（通过日志和内部状态反映结果）
     *
     * 【处理流程】
     * 1. 计算当前错误率
     * 2. 判断是否超过阈值
     * 3. 检查是否在冷却期
     * 4. 触发告警或恢复
     *
     * 【面试要点】
     * 问：为什么这个方法没有返回值？
     * 答：告警是一个副作用操作，通过日志、状态、通知等方式反映结果
     */
    public void checkAndAlert() {
        // 步骤1：计算时间范围
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusMinutes(timeWindowMinutes);

        // 步骤2：查询统计数据
        long total = logRepository.countByTimestampBetween(start, end);
        long errorCount = logRepository.countByLevelAndTimestampBetween("ERROR", start, end);

        // 如果没有日志数据，不做判断
        if (total == 0) {
            return;
        }

        // 步骤3：计算错误率
        double errorRate = (errorCount * 100.0) / total;

        log.debug("当前错误率: {:.2f}% (阈值: {}%, 窗口: {}分钟)",
                errorRate, errorRateThreshold, timeWindowMinutes);

        // 步骤4：判断是否需要告警
        if (errorRate >= errorRateThreshold) {
            // 错误率超过阈值
            triggerAlert(errorRate, errorCount, total);
        } else if (isAlerting) {
            // 错误率恢复正常
            resolveAlert(errorRate);
        }
    }

    /**
     * 【函数说明】触发告警
     *
     * 【输入】
     * - errorRate: 当前错误率
     * - errorCount: 错误数量
     * - total: 总日志数
     *
     * 【输出】无
     *
     * 【设计决策】告警内容包含什么？
     * - 当前错误率
     * - 阈值
     * - 时间窗口
     * - 错误数/总数
     * 这些信息帮助运维快速判断问题严重程度
     */
    private void triggerAlert(double errorRate, long errorCount, long total) {
        // 检查冷却时间
        // 【设计决策】为什么要冷却时间？
        // 避免同一个问题反复告警，造成告警风暴
        if (lastAlertTime != null &&
                lastAlertTime.plusSeconds(ALERT_COOLDOWN_SECONDS).isAfter(LocalDateTime.now())) {
            log.debug("告警冷却中，跳过本次告警");
            return;
        }

        // 设置告警状态
        isAlerting = true;
        lastAlertTime = LocalDateTime.now();

        // 构建告警消息
        String alertMessage = String.format(
                "🚨 错误率告警！当前错误率 %.2f%% 超过阈值 %.2f%%\n" +
                "统计窗口: 最近%d分钟\n" +
                "错误数/总数: %d/%d\n" +
                "告警时间: %s",
                errorRate, errorRateThreshold, timeWindowMinutes,
                errorCount, total,
                lastAlertTime.format(DATE_FORMATTER)
        );

        // 记录告警日志
        // 【真实场景】这里应该发送通知：邮件、短信、钉钉等
        log.error(alertMessage);

        // 记录到告警历史
        AlertRecord record = new AlertRecord(
                LocalDateTime.now(),
                "ALERT",
                errorRate,
                alertMessage
        );
        alertHistory.add(record);

        // 【真实场景扩展】
        // sendEmail(alertMessage);
        // sendSMS(alertMessage);
        // sendDingTalk(alertMessage);
        // sendSlack(alertMessage);

        log.warn("=================================================");
        log.warn(alertMessage);
        log.warn("=================================================");
    }

    /**
     * 【函数说明】告警恢复
     *
     * 【输入】currentErrorRate: 当前错误率
     *
     * 【输出】无
     *
     * 【设计决策】为什么要有恢复通知？
     * - 让运维知道问题已解决
     * - 可以关闭之前的告警工单
     * - 完整的告警生命周期管理
     */
    private void resolveAlert(double currentErrorRate) {
        isAlerting = false;

        String resolveMessage = String.format(
                "✅ 错误率恢复正常！当前错误率 %.2f%% 低于阈值 %.2f%%\n" +
                "恢复时间: %s",
                currentErrorRate, errorRateThreshold,
                LocalDateTime.now().format(DATE_FORMATTER)
        );

        log.info(resolveMessage);

        // 记录到告警历史
        AlertRecord record = new AlertRecord(
                LocalDateTime.now(),
                "RESOLVED",
                currentErrorRate,
                resolveMessage
        );
        alertHistory.add(record);
    }

    /**
     * 【函数说明】定时检查任务
     *
     * 【定时规则】每30秒执行一次
     *
     * 【设计决策】为什么用定时任务？
     * - 即使没有新日志也要检查
     * - 补充实时检查的遗漏
     * - 确保告警的完整性
     *
     * 【注解说明】
     * @Scheduled(fixedRate = 30000)
     * - fixedRate: 固定频率，单位毫秒
     * - 30000ms = 30秒
     */
    @Scheduled(fixedRate = 30000)
    public void scheduledCheck() {
        log.debug("定时检查告警条件...");
        checkAndAlert();
    }

    /**
     * 【函数说明】获取告警历史
     *
     * 【输入】limit: 返回数量限制
     *
     * 【输出】List<AlertRecord> - 告警记录列表
     *
     * 【使用场景】
     * - 查看历史告警
     * - 分析告警趋势
     */
    public List<AlertRecord> getAlertHistory(int limit) {
        int size = alertHistory.size();
        if (size <= limit) {
            return new ArrayList<>(alertHistory);
        }
        // 返回最近的N条
        return new ArrayList<>(alertHistory.subList(size - limit, size));
    }

    /**
     * 【函数说明】获取当前告警状态
     *
     * 【输入】无
     *
     * 【输出】boolean - 是否正在告警
     */
    public boolean isCurrentlyAlerting() {
        return isAlerting;
    }

    /**
     * 【函数说明】获取配置的阈值
     *
     * 【输入】无
     *
     * 【输出】double - 错误率阈值
     */
    public double getThreshold() {
        return errorRateThreshold;
    }

    /**
     * 【内部类】告警记录
     *
     * 【设计决策】为什么用内部类？
     * - 只在AlertService中使用
     * - 避免创建过多小文件
     * - 封装性更好
     */
    public static class AlertRecord {
        private final LocalDateTime timestamp;
        private final String type; // ALERT or RESOLVED
        private final double errorRate;
        private final String message;

        public AlertRecord(LocalDateTime timestamp, String type,
                          double errorRate, String message) {
            this.timestamp = timestamp;
            this.type = type;
            this.errorRate = errorRate;
            this.message = message;
        }

        // Getters
        public LocalDateTime getTimestamp() { return timestamp; }
        public String getType() { return type; }
        public double getErrorRate() { return errorRate; }
        public String getMessage() { return message; }
    }
}
