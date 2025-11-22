package com.minidog.dashboard.repository;

import com.minidog.dashboard.model.LogEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * =============================================================================
 * LogRepository.java - 日志数据访问层
 * =============================================================================
 *
 * 【文件作用】
 * 封装所有数据库操作，提供日志数据的CRUD和查询功能。
 * 继承JpaRepository获得基本的增删改查能力。
 *
 * 【设计决策】为什么用Spring Data JPA？
 * - 只需定义接口，不用写实现
 * - 方法名自动解析为SQL
 * - 减少大量样板代码
 *
 * 【Spring Data JPA方法命名规则】
 * - findBy: SELECT ... WHERE
 * - countBy: SELECT COUNT(*) WHERE
 * - deleteBy: DELETE FROM ... WHERE
 * - And/Or: 多条件组合
 * - Between: 范围查询
 * - OrderBy: 排序
 *
 * 【面试要点】
 * 问：如果查询很复杂，方法名太长怎么办？
 * 答：可以用@Query注解写JPQL或原生SQL
 */
@Repository
public interface LogRepository extends JpaRepository<LogEntry, Long> {

    /**
     * 【函数说明】按时间范围查询日志
     *
     * 【输入】
     * - start: 开始时间
     * - end: 结束时间
     *
     * 【输出】List<LogEntry> - 时间范围内的日志列表
     *
     * 【生成的SQL】
     * SELECT * FROM log_entry
     * WHERE timestamp BETWEEN ? AND ?
     * ORDER BY timestamp DESC
     *
     * 【使用场景】
     * - 查询最近5分钟的日志
     * - 查询某个时间段的日志
     */
    List<LogEntry> findByTimestampBetweenOrderByTimestampDesc(
            LocalDateTime start,
            LocalDateTime end
    );

    /**
     * 【函数说明】统计指定时间范围内某级别的日志数量
     *
     * 【输入】
     * - level: 日志级别 (INFO/WARN/ERROR)
     * - start: 开始时间
     * - end: 结束时间
     *
     * 【输出】long - 日志数量
     *
     * 【使用场景】
     * - 统计最近5分钟的ERROR数量
     * - 计算错误率
     */
    long countByLevelAndTimestampBetween(
            String level,
            LocalDateTime start,
            LocalDateTime end
    );

    /**
     * 【函数说明】统计指定时间范围内的总日志数量
     *
     * 【输入】
     * - start: 开始时间
     * - end: 结束时间
     *
     * 【输出】long - 日志总数
     *
     * 【使用场景】
     * - 计算错误率的分母
     * - 统计日志吞吐量
     */
    long countByTimestampBetween(LocalDateTime start, LocalDateTime end);

    /**
     * 【函数说明】按服务和时间范围查询日志
     *
     * 【输入】
     * - service: 服务名称
     * - start: 开始时间
     * - end: 结束时间
     *
     * 【输出】List<LogEntry> - 符合条件的日志列表
     *
     * 【使用场景】
     * - 查看某个服务的日志
     * - 排查特定服务的问题
     */
    List<LogEntry> findByServiceAndTimestampBetweenOrderByTimestampDesc(
            String service,
            LocalDateTime start,
            LocalDateTime end
    );

    /**
     * 【函数说明】获取各级别日志的统计数据
     *
     * 【输入】
     * - start: 开始时间
     * - end: 结束时间
     *
     * 【输出】List<Object[]> - 每个元素是[level, count]
     *
     * 【设计决策】为什么用@Query？
     * - 需要GROUP BY聚合查询
     * - 方法名无法表达这么复杂的逻辑
     *
     * 【JPQL说明】
     * - JPQL是面向对象的查询语言
     * - 用实体类名和属性名，不是表名和列名
     */
    @Query("SELECT l.level, COUNT(l) FROM LogEntry l " +
           "WHERE l.timestamp BETWEEN :start AND :end " +
           "GROUP BY l.level")
    List<Object[]> countByLevelGrouped(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /**
     * 【函数说明】获取各服务的错误数统计
     *
     * 【输入】
     * - start: 开始时间
     * - end: 结束时间
     *
     * 【输出】List<Object[]> - 每个元素是[service, errorCount]
     *
     * 【使用场景】
     * - 查看哪个服务错误最多
     * - 定位问题服务
     */
    @Query("SELECT l.service, COUNT(l) FROM LogEntry l " +
           "WHERE l.level = 'ERROR' AND l.timestamp BETWEEN :start AND :end " +
           "GROUP BY l.service " +
           "ORDER BY COUNT(l) DESC")
    List<Object[]> countErrorsByService(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /**
     * 【函数说明】获取最新的N条日志
     *
     * 【输入】无（使用默认参数）
     *
     * 【输出】List<LogEntry> - 最新的日志列表
     *
     * 【使用场景】
     * - 首页展示最近日志
     * - 实时日志流
     */
    List<LogEntry> findTop100ByOrderByTimestampDesc();

    /**
     * 【函数说明】按级别查询最新的日志
     *
     * 【输入】level: 日志级别
     *
     * 【输出】List<LogEntry> - 该级别最新的日志
     *
     * 【使用场景】
     * - 只看ERROR日志
     * - 过滤特定级别
     */
    List<LogEntry> findTop50ByLevelOrderByTimestampDesc(String level);

    /**
     * 【函数说明】删除指定时间之前的旧日志
     *
     * 【输入】before: 时间点
     *
     * 【输出】无
     *
     * 【使用场景】
     * - 定期清理过期数据
     * - 控制数据库大小
     *
     * 【设计决策】为什么需要清理？
     * - 内存数据库容量有限
     * - 历史数据价值递减
     * - 保持查询性能
     */
    void deleteByTimestampBefore(LocalDateTime before);
}
