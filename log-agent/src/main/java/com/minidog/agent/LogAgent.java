package com.minidog.agent;

import com.google.gson.Gson;
import okhttp3.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * =============================================================================
 * LogAgent.java - 日志采集代理
 * =============================================================================
 *
 * 【文件作用】
 * 监控日志文件的变化，将新增的日志行实时发送到Dashboard Server。
 * 类似于Filebeat、Fluentd等日志采集工具的简化版。
 *
 * 【核心功能】
 * 1. 文件监控：检测日志文件的新增内容
 * 2. 增量读取：只读取新增的行，不重复读取
 * 3. HTTP发送：将日志发送到Server的REST API
 * 4. 批量上报：收集多条日志后批量发送，提高效率
 *
 * 【设计决策】为什么不用WatchService？
 * Java的WatchService有平台兼容性问题，且在某些系统上有延迟。
 * 轮询方式虽然看起来"笨"，但更可靠、更易控制。
 *
 * 【面试要点】
 * 问：真实的日志采集系统怎么设计？
 * 答：
 * 1. 支持多种输入源（文件、网络、容器）
 * 2. 支持多种输出目标（ES、Kafka、S3）
 * 3. 断点续传（记录offset）
 * 4. 背压控制（防止打爆下游）
 * 5. 过滤和转换（Grok、JSON解析）
 */
public class LogAgent {

    // =============================================================================
    // 配置常量
    // =============================================================================

    /**
     * Dashboard Server地址
     * 【配置】可以通过命令行参数覆盖
     */
    private static String SERVER_URL = "http://localhost:8080/api/logs/batch";

    /**
     * 轮询间隔（毫秒）
     * 【设计决策】为什么1秒？
     * - 太短：CPU开销大
     * - 太长：延迟高
     * - 1秒是个折中值
     */
    private static final int POLL_INTERVAL_MS = 1000;

    /**
     * 批量发送阈值
     * 【设计决策】为什么收集10条再发送？
     * - 减少HTTP请求次数
     * - 提高吞吐量
     * - 平衡实时性和效率
     */
    private static final int BATCH_SIZE = 10;

    /**
     * 最大等待时间（毫秒）
     * 【作用】即使没到BATCH_SIZE，超过这个时间也要发送
     * 【设计决策】防止日志量小时延迟太高
     */
    private static final int MAX_WAIT_MS = 5000;

    // =============================================================================
    // 运行时变量
    // =============================================================================

    /**
     * HTTP客户端
     * 【设计决策】为什么用OkHttp？
     * - 性能好，连接池复用
     * - API简洁易用
     * - 广泛使用，文档丰富
     */
    private final OkHttpClient httpClient;

    /**
     * JSON序列化工具
     */
    private final Gson gson;

    /**
     * 待发送的日志缓冲区
     * 【设计决策】为什么用ArrayList？
     * - 顺序读写
     * - 支持批量操作
     * - 单线程访问，不需要线程安全
     */
    private final List<String> logBuffer;

    /**
     * 上次发送时间
     */
    private long lastSendTime;

    /**
     * 监控的文件路径
     */
    private final String filePath;

    /**
     * 文件读取位置（字节偏移）
     * 【关键点】记录已读取的位置，实现增量读取
     */
    private long filePosition;

    /**
     * 运行标志
     */
    private volatile boolean running;

    /**
     * 【构造函数】初始化LogAgent
     *
     * 【输入】filePath: 要监控的日志文件路径
     *
     * 【初始化内容】
     * 1. HTTP客户端（带超时配置）
     * 2. JSON工具
     * 3. 日志缓冲区
     */
    public LogAgent(String filePath) {
        this.filePath = filePath;
        this.filePosition = 0;
        this.running = true;
        this.logBuffer = new ArrayList<>();
        this.lastSendTime = System.currentTimeMillis();

        // 配置HTTP客户端
        // 【设计决策】超时设置
        // - 连接超时5秒：防止服务器无响应
        // - 读写超时10秒：给服务器处理时间
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();

        this.gson = new Gson();
    }

    /**
     * 【函数说明】启动日志采集
     *
     * 【输入】无
     *
     * 【输出】无
     *
     * 【处理流程】
     * 1. 检查文件是否存在
     * 2. 定位到文件末尾（只采集新增内容）
     * 3. 进入轮询循环
     * 4. 检测文件变化，读取新增行
     * 5. 批量发送到Server
     *
     * 【面试要点】
     * 问：如果程序重启，怎么避免重复采集？
     * 答：可以把filePosition持久化到文件或数据库
     */
    public void start() {
        System.out.println("==============================================");
        System.out.println("  Mini-Datadog Log Agent 启动");
        System.out.println("  监控文件: " + filePath);
        System.out.println("  目标服务: " + SERVER_URL);
        System.out.println("==============================================");

        File file = new File(filePath);

        // 检查文件是否存在
        // 【设计决策】如果文件不存在，等待它创建
        while (!file.exists() && running) {
            System.out.println("等待文件创建: " + filePath);
            sleep(POLL_INTERVAL_MS);
        }

        // 定位到文件末尾，只采集新增内容
        // 【设计决策】为什么从末尾开始？
        // - 避免采集历史数据
        // - 启动更快
        // - 如果需要采集历史，可以设置filePosition=0
        filePosition = file.length();
        System.out.println("初始位置: " + filePosition + " bytes");

        // 主循环
        while (running) {
            try {
                // 检查文件变化并读取新内容
                checkAndReadFile(file);

                // 检查是否需要发送
                checkAndSend();

                // 等待下一次轮询
                sleep(POLL_INTERVAL_MS);

            } catch (Exception e) {
                System.err.println("处理异常: " + e.getMessage());
                e.printStackTrace();
            }
        }

        // 退出前发送剩余日志
        if (!logBuffer.isEmpty()) {
            sendLogs();
        }

        System.out.println("Log Agent 已停止");
    }

    /**
     * 【函数说明】检查文件并读取新增内容
     *
     * 【输入】file: File对象
     *
     * 【输出】无（读取的内容放入logBuffer）
     *
     * 【处理逻辑】
     * 1. 比较当前文件大小和上次位置
     * 2. 如果文件变小了，说明被截断，从头开始
     * 3. 如果文件变大了，读取新增部分
     *
     * 【关键技术】RandomAccessFile
     * - 支持随机访问（seek到指定位置）
     * - 可以只读取文件的一部分
     */
    private void checkAndReadFile(File file) throws IOException {
        long currentLength = file.length();

        // 文件被截断的处理
        // 【场景】logrotate等工具会截断日志文件
        if (currentLength < filePosition) {
            System.out.println("检测到文件被截断，从头开始读取");
            filePosition = 0;
        }

        // 有新内容
        if (currentLength > filePosition) {
            // 使用RandomAccessFile进行随机访问
            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                // 定位到上次读取的位置
                raf.seek(filePosition);

                String line;
                // 逐行读取
                // 【注意】readLine()会自动处理换行符
                while ((line = raf.readLine()) != null) {
                    // 处理中文编码
                    // 【坑点】RandomAccessFile.readLine()用ISO-8859-1编码
                    // 需要转换为UTF-8
                    line = new String(line.getBytes("ISO-8859-1"), "UTF-8");

                    if (!line.trim().isEmpty()) {
                        logBuffer.add(line);
                        System.out.println("[读取] " + line);
                    }
                }

                // 更新位置
                filePosition = raf.getFilePointer();
            }
        }
    }

    /**
     * 【函数说明】检查是否需要发送日志
     *
     * 【发送条件】满足以下任一条件就发送：
     * 1. 缓冲区达到BATCH_SIZE
     * 2. 距离上次发送超过MAX_WAIT_MS
     *
     * 【设计决策】为什么要两个条件？
     * - 条件1保证批量效率
     * - 条件2保证实时性（日志量小时不会等太久）
     */
    private void checkAndSend() {
        if (logBuffer.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        boolean shouldSend = false;

        // 条件1：达到批量阈值
        if (logBuffer.size() >= BATCH_SIZE) {
            shouldSend = true;
        }

        // 条件2：超过最大等待时间
        if (now - lastSendTime >= MAX_WAIT_MS) {
            shouldSend = true;
        }

        if (shouldSend) {
            sendLogs();
            lastSendTime = now;
        }
    }

    /**
     * 【函数说明】发送日志到Server
     *
     * 【输入】无（从logBuffer读取）
     *
     * 【输出】无
     *
     * 【请求格式】
     * POST /api/logs/batch
     * {
     *   "logs": ["日志1", "日志2", ...]
     * }
     *
     * 【错误处理】
     * - 网络错误：记录日志，保留数据下次重试
     * - HTTP错误：记录错误码
     *
     * 【面试要点】
     * 问：发送失败怎么办？
     * 答：
     * 1. 实现重试机制
     * 2. 持久化到本地文件
     * 3. 死信队列
     */
    private void sendLogs() {
        if (logBuffer.isEmpty()) {
            return;
        }

        // 复制一份，避免发送过程中被修改
        List<String> toSend = new ArrayList<>(logBuffer);

        // 构建请求体
        Map<String, List<String>> payload = new HashMap<>();
        payload.put("logs", toSend);
        String jsonBody = gson.toJson(payload);

        // 构建HTTP请求
        RequestBody body = RequestBody.create(
                jsonBody,
                MediaType.parse("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(SERVER_URL)
                .post(body)
                .build();

        // 发送请求
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                System.out.println("[发送成功] " + toSend.size() + " 条日志");
                // 发送成功，清空缓冲区
                logBuffer.clear();
            } else {
                System.err.println("[发送失败] HTTP " + response.code());
                // 发送失败，保留数据下次重试
            }
        } catch (IOException e) {
            System.err.println("[发送异常] " + e.getMessage());
            // 网络异常，保留数据下次重试
        }
    }

    /**
     * 【函数说明】停止采集
     */
    public void stop() {
        running = false;
    }

    /**
     * 【辅助函数】线程休眠
     */
    private void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 【函数说明】主函数入口
     *
     * 【命令行参数】
     * args[0]: 日志文件路径（必填）
     * args[1]: Server URL（可选）
     *
     * 【使用示例】
     * java -jar log-agent.jar ./logs/app.log
     * java -jar log-agent.jar ./logs/app.log http://server:8080/api/logs/batch
     */
    public static void main(String[] args) {
        // 解析命令行参数
        String filePath = "./logs/app.log";  // 默认值

        if (args.length >= 1) {
            filePath = args[0];
        }

        if (args.length >= 2) {
            SERVER_URL = args[1];
        }

        // 创建并启动Agent
        LogAgent agent = new LogAgent(filePath);

        // 注册关闭钩子，优雅停止
        // 【设计决策】为什么需要关闭钩子？
        // - 用户按Ctrl+C时能正确处理
        // - 发送剩余的日志
        // - 释放资源
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n收到停止信号，正在关闭...");
            agent.stop();
        }));

        // 启动采集
        agent.start();
    }
}
