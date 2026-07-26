package cn.net.rjnetwork.xianyu.manager.system.controller;

import cn.net.rjnetwork.xianyu.manager.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    @GetMapping("/info")
    public ApiResponse<Map<String, Object>> info() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("appName", "闲鱼多账号管理平台");
        info.put("version", "1.0.0");
        info.put("database", "SQLite3");
        info.put("cache", "Caffeine In-Memory");
        info.put("javaVersion", System.getProperty("java.version"));
        info.put("springBootVersion", "3.5.4");
        return ApiResponse.ok(info);
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "UP");
        health.put("timestamp", System.currentTimeMillis());

        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memoryBean.getHeapMemoryUsage();
        MemoryUsage nonHeap = memoryBean.getNonHeapMemoryUsage();
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();

        Map<String, Object> memory = new LinkedHashMap<>();
        memory.put("heapUsedBytes", heap.getUsed());
        memory.put("heapCommittedBytes", heap.getCommitted());
        memory.put("heapMaxBytes", heap.getMax());
        memory.put("nonHeapUsedBytes", nonHeap.getUsed());
        memory.put("nonHeapCommittedBytes", nonHeap.getCommitted());
        memory.put("threadCount", threadBean.getThreadCount());
        memory.put("daemonThreadCount", threadBean.getDaemonThreadCount());
        memory.put("peakThreadCount", threadBean.getPeakThreadCount());
        health.put("runtime", memory);
        return ApiResponse.ok(health);
    }
}
