package com.minidog.dashboard.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * =============================================================================
 * LogEntry.java - 日志条目实体类
 * =============================================================================
 *
 * 【文件作用】
 * 定义日志数据的结构，对应数据库中的log_entry表。
 * 这是整个系统的核心数据模型。
 *
 * 【设计决策】为什么用JPA实体而不是普通POJO？
 * - JPA实体可以自动映射到数据库表
 * - 支持自动建表，不需要手写DDL
 * - 查询结果自动映射为对象
 *
 * 【Lombok注解说明】
 * @Data: 自动生成getter/setter/toString/equals/hashCode
 * @NoArgsConstructor: 生成无参构造函数（JPA需要）
 * @AllArgsConstructor: 生成全参构造函数
 *
 * 【字段设计思路】
 * 参考了ELK Stack中日志的标准字段，包括：
 * - 时间戳：什么时候发生
 * - 级别：严重程度
 * - 来源：哪个服务/模块
 * - 消息：具体内容
 *
 * 【面试要点】
 * 问：为什么要单独抽取service字段？
 * 答：便于按服务过滤和聚合统计，支持微服务架构下的多服务监控
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "log_entry", indexes = {
    // 【设计决策】为什么要建索引？
    // - timestamp索引：加速时间范围查询
    // - level索引：加速按级别过滤
    // - service索引：加速按服务过滤
    @Index(name = "idx_timestamp", columnList = "timestamp"),
    @Index(name = "idx_level", columnList = "level"),
    @Index(name = "idx_service", columnList = "service")
})
public class LogEntry {

    /**
     * 主键ID
     * 【设计决策】为什么用自增ID？
     * - 简单高效
     * - 适合单机部署
     * - 如果要分布式，可以改用UUID或雪花算法
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 日志时间戳
     * 【设计决策】为什么用LocalDateTime？
     * - Java 8+的新日期API，更好用
     * - 不可变对象，线程安全
     * - 比Date类型更语义化
     */
    @Column(nullable = false)
    private LocalDateTime timestamp;

    /**
     * 日志级别
     * 【取值】INFO, WARN, ERROR
     * 【设计决策】为什么用String而不是Enum？
     * - 数据库存储更直观
     * - 扩展新级别更方便
     * - 查询语句更易读
     */
    @Column(nullable = false, length = 10)
    private String level;

    /**
     * 服务来源
     * 【作用】标识日志来自哪个微服务
     * 【示例】UserService, PaymentService
     */
    @Column(nullable = false, length = 100)
    private String service;

    /**
     * 日志消息内容
     * 【设计决策】为什么用TEXT类型？
     * - 日志消息可能很长
     * - VARCHAR有长度限制
     * - TEXT可以存储大文本
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    /**
     * 记录创建时间
     * 【作用】记录数据入库时间，区别于日志本身的时间戳
     * 【用途】可以用来分析日志采集的延迟
     */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /**
     * 【函数说明】在保存前自动设置创建时间
     *
     * 【JPA生命周期回调】
     * @PrePersist: 在实体被持久化之前调用
     *
     * 【设计决策】为什么用回调而不是在Service层设置？
     * - 统一处理，不会遗漏
     * - 职责清晰，实体管理自己的元数据
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
