package cn.nebulaedata.cccs.acutor_module.controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.management.OperatingSystemMXBean;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.management.*;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Controller
public class MetricsController {
    
    private final OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private final ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
    private final ClassLoadingMXBean classLoadingBean = ManagementFactory.getClassLoadingMXBean();
    private final List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
    
    // 存储上次网络数据，用于计算速度
    private long lastReceivedBytes = 0;
    private long lastSentBytes = 0;
    private long lastTimestamp = 0;
    
    // 四舍五入工具方法
    private double round(double value, int places) {
        try {
            if (places < 0) throw new IllegalArgumentException("小数位数不能为负数");
            
            long factor = (long) Math.pow(10, places);
            value = value * factor;
            long tmp = Math.round(value);
            return (double) tmp / factor;
        } catch (Exception e) {
            System.err.println("四舍五入计算时发生异常: " + e.getMessage());
            e.printStackTrace();
            return 0.0;
        }
    }
    
    // 获取系统指标
    @GetMapping("/metrics/system")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        try {
            // JVM堆内存信息
            MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
            long heapUsedBytes = heapUsage.getUsed();
            long heapMaxBytes = heapUsage.getMax(); // 可能为 -1 或 Long.MAX_VALUE
            long heapCommittedBytes = heapUsage.getCommitted();
            
            metrics.put("heapUsedMB", round(heapUsedBytes / (1024.0 * 1024.0), 2));
            metrics.put("heapMaxMB", heapMaxBytes > 0 ? round(heapMaxBytes / (1024.0 * 1024.0), 2) : -1);
            metrics.put("heapCommittedMB", round(heapCommittedBytes / (1024.0 * 1024.0), 2));
            
            if (heapMaxBytes > 0) {
                double heapUsagePercent = (heapUsedBytes * 100.0) / heapMaxBytes;
                metrics.put("heapUsagePercent", round(heapUsagePercent, 2));
            } else {
                metrics.put("heapUsagePercent", -1);
            }
            
            // JVM非堆内存信息
            MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
            long nonHeapUsedBytes = nonHeapUsage.getUsed();
            metrics.put("nonHeapUsedMB", round(nonHeapUsedBytes / (1024.0 * 1024.0), 2));
            
            // CPU使用率信息
            double systemCpuLoad = osBean.getSystemCpuLoad(); // 系统整体 CPU 使用率
            double processCpuLoad = osBean.getProcessCpuLoad(); // 当前 JVM 进程 CPU 使用率
            
            metrics.put("systemCpuLoadPercent", systemCpuLoad >= 0 ? round(systemCpuLoad * 100.0, 2) : -1);
            metrics.put("processCpuLoadPercent", processCpuLoad >= 0 ? round(processCpuLoad * 100.0, 2) : -1);
            
            // 系统信息
            metrics.put("availableProcessors", osBean.getAvailableProcessors());
            metrics.put("systemLoadAverage", osBean.getSystemLoadAverage()); // Unix/Linux 平均负载
            
            // 系统内存信息
            long totalPhysicalMemorySize = osBean.getTotalPhysicalMemorySize();
            long freePhysicalMemorySize = osBean.getFreePhysicalMemorySize();
            long usedPhysicalMemorySize = totalPhysicalMemorySize - freePhysicalMemorySize;
            double memoryUsagePercent = (usedPhysicalMemorySize * 100.0) / totalPhysicalMemorySize;
            
            metrics.put("totalPhysicalMemoryMB", round(totalPhysicalMemorySize / (1024.0 * 1024.0), 2));
            metrics.put("freePhysicalMemoryMB", round(freePhysicalMemorySize / (1024.0 * 1024.0), 2));
            metrics.put("usedPhysicalMemoryMB", round(usedPhysicalMemorySize / (1024.0 * 1024.0), 2));
            metrics.put("systemMemoryUsagePercent", round(memoryUsagePercent, 2));
            
            // 线程信息
            metrics.put("threadCount", threadBean.getThreadCount());
            metrics.put("peakThreadCount", threadBean.getPeakThreadCount());
            metrics.put("threads", threadBean); // 添加完整对象供前端使用
            
            // 类加载信息
            metrics.put("loadedClassCount", classLoadingBean.getLoadedClassCount());
            metrics.put("unloadedClassCount", classLoadingBean.getUnloadedClassCount());
            metrics.put("classes", classLoadingBean); // 添加完整对象供前端使用
            
            // 垃圾回收信息
            Map<String, Map<String, Object>> gcInfo = new HashMap<>();
            long totalGcCount = 0;
            long totalGcTime = 0;
            
            for (GarbageCollectorMXBean gcBean : gcBeans) {
                Map<String, Object> gcData = new HashMap<>();
                long gcCount = gcBean.getCollectionCount();
                long gcTime = gcBean.getCollectionTime();
                
                gcData.put("collectionCount", gcCount);
                gcData.put("collectionTime", gcTime);
                
                gcInfo.put(gcBean.getName(), gcData);
                
                totalGcCount += gcCount;
                totalGcTime += gcTime;
            }
            
            metrics.put("gc", gcInfo);
            metrics.put("totalGcCount", totalGcCount);
            metrics.put("totalGcTime", totalGcTime);
            metrics.put("memory", memoryBean);
            
            // GPU信息
            metrics.put("gpuInfo", getGpuInfo());
            
            // 网络速度信息
            metrics.put("networkSpeed", getNetworkSpeed());
            
        } catch (Exception e) {
            metrics.put("error", "获取系统指标时发生错误: " + e.getMessage());
            e.printStackTrace();
        }
        
        return ResponseEntity.ok(metrics);
    }
    
    // 获取Docker容器信息
    @GetMapping("/metrics/docker/containers")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getDockerContainers() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 检查Docker是否可用
            boolean dockerAvailable = checkDockerAvailability();
            result.put("dockerAvailable", dockerAvailable);
            
            if (!dockerAvailable) {
                result.put("error", "Docker不可用");
                // 添加操作系统信息
                String osName = System.getProperty("os.name");
                result.put("osName", osName);
                
                // 检查Docker套接字是否存在
                File dockerSocket = new File("/var/run/docker.sock");
                result.put("dockerSocketExists", dockerSocket.exists());
                
                // 尝试检查Docker守护进程
                try {
                    Process process = Runtime.getRuntime().exec("docker info");
                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                    String errorLine = reader.readLine();
                    if (errorLine != null) {
                        result.put("daemonCheckError", errorLine);
                    }
                    reader.close();
                } catch (Exception e) {
                    System.err.println("检查Docker守护进程时发生异常: " + e.getMessage());
                    e.printStackTrace();
                    result.put("daemonCheckError", e.getMessage());
                }
                
                return ResponseEntity.ok(result);
            }
            
            // 执行docker ps命令获取容器信息
            Process process = null;
            BufferedReader reader = null;
            BufferedReader errorReader = null;
            
            try {
                process = Runtime.getRuntime().exec("docker ps --format \"{{.ID}}|{{.Names}}|{{.Status}}|{{.Image}}\"");
                reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                
                String line;
                List<Map<String, String>> containers = new ArrayList<>();
                
                // 读取容器信息
                while ((line = reader.readLine()) != null) {
                    if (!line.trim().isEmpty()) {
                        String[] parts = line.split("\\|");
                        if (parts.length >= 4) {
                            Map<String, String> container = new HashMap<>();
                            container.put("id", parts[0].trim());
                            container.put("name", parts[1].trim());
                            container.put("status", parts[2].trim());
                            container.put("image", parts[3].trim());
                            containers.add(container);
                        }
                    }
                }
                
                // 读取错误输出
                StringBuilder errorOutput = new StringBuilder();
                while ((line = errorReader.readLine()) != null) {
                    errorOutput.append(line).append("\n");
                }
                
                int exitCode = process.waitFor();
                
                // 如果命令执行出错，返回错误信息
                if (exitCode != 0) {
                    String errorMsg = "执行docker命令失败，退出码: " + exitCode;
                    if (errorOutput.length() > 0) {
                        errorMsg += "，错误信息: " + errorOutput.toString();
                    }
                    System.err.println(errorMsg);
                    result.put("error", errorMsg);
                    result.put("dockerAvailable", false);
                    result.put("debug", "错误输出: " + errorOutput.toString());
                    
                    // 特别处理权限问题
                    if (errorOutput.toString().contains("permission denied")) {
                        result.put("solution", "权限被拒绝。请尝试以下解决方案：\n" +
                                  "1. 将当前用户添加到docker组: sudo usermod -aG docker $USER\n" +
                                  "2. 重启Docker服务: sudo systemctl restart docker\n" +
                                  "3. 或者在运行容器时使用更高权限: docker run --privileged ...\n" +
                                  "4. 检查/var/run/docker.sock文件权限\n" +
                                  "5. 在docker-compose.yml中添加privileged: true配置项\n" +
                                  "6. 注销并重新登录，或者运行newgrp docker命令");
                    }
                    
                    return ResponseEntity.ok(result);
                }
                
                result.put("containerCount", containers.size());
                // 直接返回容器数组而不是JSON字符串
                result.put("containers", containers);
            } finally {
                // 确保资源被正确关闭
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (Exception e) {
                        System.err.println("关闭标准输出读取器时发生异常: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
                if (errorReader != null) {
                    try {
                        errorReader.close();
                    } catch (Exception e) {
                        System.err.println("关闭错误输出读取器时发生异常: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
            
        } catch (Exception e) {
            String errorMsg = "获取容器信息时发生错误: " + e.getMessage();
            System.err.println(errorMsg);
            e.printStackTrace();
            result.put("error", errorMsg);
            result.put("debug", "原始数据: " + e.getMessage());
            
            // 特别处理权限相关的异常
            if (e.getMessage() != null && 
                (e.getMessage().contains("Permission denied") || 
                 e.getMessage().contains("permission denied") ||
                 e.getMessage().contains("Got permission denied"))) {
                result.put("solution", "Docker权限被拒绝。请按以下步骤检查和解决问题：\n" +
                          "1. 确保容器已正确挂载Docker套接字:\n" +
                          "   - 在docker-compose.yml中添加: volumes:\n" +
                          "   - 添加: - /var/run/docker.sock:/var/run/docker.sock\n\n" +
                          "2. 确保容器用户已添加到docker组:\n" +
                          "   - 在Dockerfile中添加: adduser appuser docker\n\n" +
                          "3. 在docker-compose.yml中添加group_add配置:\n" +
                          "   - 添加: group_add:\n" +
                          "   - 添加: - 999  # docker组的标准GID\n\n" +
                          "4. 或者使用privileged模式运行容器（不推荐生产环境）:\n" +
                          "   - 在docker-compose.yml中添加: privileged: true\n\n" +
                          "5. 重启Docker服务:\n" +
                          "   - 运行: sudo systemctl restart docker\n\n" +
                          "6. 检查/var/run/docker.sock文件权限:\n" +
                          "   - 运行: sudo chmod 666 /var/run/docker.sock\n\n" +
                          "7. 将当前用户添加到docker组（在宿主机上执行）:\n" +
                          "   - 运行: sudo usermod -aG docker $USER\n" +
                          "   - 然后注销并重新登录");
            }
            
            result.put("dockerCheck", checkDockerAvailability());
        }
        
        return ResponseEntity.ok(result);
    }
    
    // 检查Docker是否可用
    private boolean checkDockerAvailability() {
        try {
            Process process = Runtime.getRuntime().exec("docker version");
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            System.err.println("检查Docker可用性时发生异常: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    // 获取特定容器的日志
    @GetMapping("/metrics/docker/logs")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getDockerLogs(String containerId, Integer lines) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            System.out.println("收到获取容器日志请求: containerId=" + containerId + ", lines=" + lines);
            
            // 改进参数验证
            if (containerId == null || containerId.trim().isEmpty()) {
                String errorMsg = "容器ID不能为空";
                System.out.println(errorMsg);
                result.put("error", errorMsg);
                return ResponseEntity.ok(result);
            }
            
            // 去除容器ID两端的空格
            containerId = containerId.trim();
            
            // 默认获取最后100行日志
            if (lines == null || lines <= 0) {
                lines = 100;
            }
            
            // 限制最大日志行数
            if (lines > 1000) {
                lines = 1000;
            }
            
            // 检查Docker是否可用
            if (!checkDockerAvailability()) {
                String errorMsg = "Docker不可用，请检查权限配置";
                System.err.println(errorMsg);
                result.put("error", errorMsg);
                result.put("solution", "请确保：\n" +
                          "1. 容器已挂载Docker套接字: -v /var/run/docker.sock:/var/run/docker.sock\n" +
                          "2. 容器以特权模式运行: --privileged\n" +
                          "3. 应用用户已添加到docker组\n" +
                          "4. Docker客户端已安装在容器中");
                return ResponseEntity.ok(result);
            }
            
            // 获取容器日志
            String[] command = {"docker", "logs", "--tail", String.valueOf(lines), containerId};
            System.out.println("执行命令: " + String.join(" ", command));
            
            Process process = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"));
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream(), "UTF-8"));
            
            StringBuilder logs = new StringBuilder();
            StringBuilder errorOutput = new StringBuilder();
            
            // 使用线程分别读取标准输出和错误输出，避免阻塞
            Thread outputThread = new Thread(() -> {
                try {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        logs.append(line).append("\n");
                    }
                } catch (Exception e) {
                    System.err.println("读取标准输出时发生异常: " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    try {
                        if (reader != null) {
                            reader.close();
                        }
                    } catch (Exception e) {
                        System.err.println("关闭标准输出读取器时发生异常: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            });
            
            Thread errorThread = new Thread(() -> {
                try {
                    String line;
                    while ((line = errorReader.readLine()) != null) {
                        errorOutput.append(line).append("\n");
                    }
                } catch (Exception e) {
                    System.err.println("读取错误输出时发生异常: " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    try {
                        if (errorReader != null) {
                            errorReader.close();
                        }
                    } catch (Exception e) {
                        System.err.println("关闭错误输出读取器时发生异常: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            });
            
            outputThread.start();
            errorThread.start();
            
            // 等待线程执行完成，设置超时时间
            try {
                outputThread.join(10000); // 10秒超时
                errorThread.join(10000);  // 10秒超时
            } catch (InterruptedException e) {
                System.err.println("等待线程完成时被中断: " + e.getMessage());
                Thread.currentThread().interrupt(); // 恢复中断状态
            }
            
            int exitCode = process.waitFor();
            System.out.println("命令执行完成，退出码: " + exitCode);
            
            if (errorOutput.length() > 0) {
                System.out.println("错误输出: " + errorOutput.toString());
            }
            
            // 如果命令执行出错，返回错误信息
            if (exitCode != 0) {
                String errorMsg = "执行docker logs命令失败，退出码: " + exitCode;
                if (errorOutput.length() > 0) {
                    errorMsg += "，错误信息: " + errorOutput.toString();
                }
                System.err.println(errorMsg);
                
                // 特别处理权限问题
                if (errorOutput.toString().contains("permission denied") || 
                    errorOutput.toString().contains("access denied") ||
                    errorOutput.toString().contains("Are you trying to connect to a TLS-enabled daemon without TLS?")) {
                    result.put("solution", "权限被拒绝。请尝试以下解决方案：\n" +
                              "1. 确保容器已挂载Docker套接字: -v /var/run/docker.sock:/var/run/docker.sock\n" +
                              "2. 确保容器以特权模式运行: --privileged\n" +
                              "3. 确保应用用户已添加到docker组\n" +
                              "4. 重启Docker服务: sudo systemctl restart docker\n" +
                              "5. 检查/var/run/docker.sock文件权限\n" +
                              "6. 在docker-compose.yml中添加privileged: true配置项");
                }
                
                result.put("error", errorMsg);
                return ResponseEntity.ok(result);
            }
            
            result.put("logs", logs.toString());
            result.put("containerId", containerId);
            System.out.println("成功返回日志，日志行数: " + (logs.toString().split("\n").length - 1));
            
        } catch (Exception e) {
            String errorMsg = "获取容器日志时发生错误: " + e.getMessage();
            System.err.println(errorMsg);
            e.printStackTrace();
            
            // 特别处理权限相关的异常
            if (e.getMessage() != null && (e.getMessage().contains("Permission denied") || 
                e.getMessage().contains("permission denied"))) {
                result.put("solution", "权限被拒绝。请尝试以下解决方案：\n" +
                          "1. 确保容器已挂载Docker套接字: -v /var/run/docker.sock:/var/run/docker.sock\n" +
                          "2. 确保容器以特权模式运行: --privileged\n" +
                          "3. 确保应用用户已添加到docker组\n" +
                          "4. 重启Docker服务: sudo systemctl restart docker\n" +
                          "5. 检查/var/run/docker.sock文件权限\n" +
                          "6. 在docker-compose.yml中添加privileged: true配置项");
            }
            
            result.put("error", errorMsg);
        }
        
        return ResponseEntity.ok(result);
    }
    
    // 获取特定容器的实时日志流信息
    @GetMapping("/metrics/docker/logs/stream")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> streamDockerLogs(String containerId) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            if (containerId == null || containerId.isEmpty()) {
                result.put("error", "容器ID不能为空");
                return ResponseEntity.ok(result);
            }
            
            result.put("wsEndpoint", "/ws/docker/logs");
            result.put("containerId", containerId);
            result.put("message", "请通过WebSocket连接获取实时日志");
            
        } catch (Exception e) {
            result.put("error", "获取日志流端点信息时发生错误: " + e.getMessage());
            e.printStackTrace();
        }
        
        return ResponseEntity.ok(result);
    }
    
    // 获取GPU信息
    private Map<String, Object> getGpuInfo() {
        Map<String, Object> gpuInfo = new HashMap<>();
        
        try {
            // 尝试执行nvidia-smi命令获取GPU信息
            Process process = Runtime.getRuntime().exec("nvidia-smi --query-gpu=index,name,utilization.gpu,memory.used,memory.total --format=csv,noheader,nounits");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            
            String line;
            StringBuilder gpuData = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                gpuData.append(line).append(";");
            }
            
            process.waitFor();
            reader.close();
            
            String[] gpuEntries = gpuData.toString().split(";");
            gpuInfo.put("gpuCount", gpuEntries.length > 0 && !gpuEntries[0].isEmpty() ? gpuEntries.length : 0);
            
            // 解析第一个GPU的信息
            if (gpuEntries.length > 0 && !gpuEntries[0].isEmpty()) {
                String[] firstGpuData = gpuEntries[0].split(",");
                if (firstGpuData.length >= 5) {
                    gpuInfo.put("gpuName", firstGpuData[1].trim());
                    gpuInfo.put("gpuUtilization", Integer.parseInt(firstGpuData[2].trim()));
                    
                    int memoryUsed = Integer.parseInt(firstGpuData[3].trim());
                    int memoryTotal = Integer.parseInt(firstGpuData[4].trim());
                    gpuInfo.put("gpuMemoryUsed", memoryUsed);
                    gpuInfo.put("gpuMemoryTotal", memoryTotal);
                    gpuInfo.put("gpuMemoryUtilization", memoryTotal > 0 ? round((memoryUsed * 100.0) / memoryTotal, 2) : 0);
                }
            }
        } catch (NumberFormatException e) {
            System.err.println("解析GPU信息时发生数字格式异常: " + e.getMessage());
            e.printStackTrace();
            gpuInfo.put("error", "无法解析GPU信息: " + e.getMessage());
            gpuInfo.put("gpuCount", 0);
        } catch (Exception e) {
            System.err.println("获取GPU信息时发生异常: " + e.getMessage());
            e.printStackTrace();
            gpuInfo.put("error", "无法获取GPU信息: " + e.getMessage());
            gpuInfo.put("gpuCount", 0);
        }
        
        return gpuInfo;
    }
    
    // 获取网络速度信息
    private Map<String, Object> getNetworkSpeed() {
        Map<String, Object> networkSpeed = new HashMap<>();
        BufferedReader reader = null; // 将reader声明在方法开始处，确保在整个方法中可见
        
        try {
            String osName = System.getProperty("os.name").toLowerCase();
            Process process = null;
            
            if (osName.contains("win")) {
                // Windows系统
                process = Runtime.getRuntime().exec("netstat -e");
                reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                
                // 跳过前几行，读取包含字节统计的行
                reader.readLine(); // 标题行
                reader.readLine(); // 标题说明行
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("Bytes")) {
                        String[] parts = line.trim().split("\\s+");
                        if (parts.length >= 3) {
                            long receivedBytes = Long.parseLong(parts[1]);
                            long sentBytes = Long.parseLong(parts[2]);
                            
                            long currentTimestamp = System.currentTimeMillis();
                            networkSpeed.put("receivedBytes", receivedBytes);
                            networkSpeed.put("sentBytes", sentBytes);
                            networkSpeed.put("lastReceivedBytes", lastReceivedBytes);
                            networkSpeed.put("lastSentBytes", lastSentBytes);
                            networkSpeed.put("lastTimestamp", lastTimestamp);
                            networkSpeed.put("currentTimestamp", currentTimestamp);
                            
                            if (lastTimestamp > 0) {
                                double timeDiffSeconds = (currentTimestamp - lastTimestamp) / 1000.0;
                                if (timeDiffSeconds > 0) {
                                    double receivedKbps = ((receivedBytes - lastReceivedBytes) * 8.0) / (timeDiffSeconds * 1000.0);
                                    double sentKbps = ((sentBytes - lastSentBytes) * 8.0) / (timeDiffSeconds * 1000.0);
                                    
                                    networkSpeed.put("receivedKbps", round(receivedKbps, 2));
                                    networkSpeed.put("sentKbps", round(sentKbps, 2));
                                }
                            }
                            
                            // 更新上次数据
                            lastReceivedBytes = receivedBytes;
                            lastSentBytes = sentBytes;
                            lastTimestamp = currentTimestamp;
                            
                            networkSpeed.put("totalReceivedMB", round(receivedBytes / (1024.0 * 1024.0), 2));
                            networkSpeed.put("totalSentMB", round(sentBytes / (1024.0 * 1024.0), 2));
                            
                            break;
                        }
                    }
                }
            } else {
                // Unix/Linux/macOS系统
                process = Runtime.getRuntime().exec("cat /proc/net/dev");
                reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                
                reader.readLine(); // 标题行
                reader.readLine(); // 标题说明行
                
                long totalReceivedBytes = 0;
                long totalSentBytes = 0;
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.startsWith("Inter") && !line.startsWith("face")) { // 跳过标题行
                        String[] parts = line.split("\\s+");
                        if (parts.length >= 10) {
                            // 忽略lo接口
                            if (!parts[0].startsWith("lo:")) {
                                totalReceivedBytes += Long.parseLong(parts[1]);
                                totalSentBytes += Long.parseLong(parts[9]);
                            }
                        }
                    }
                }
                
                long currentTimestamp = System.currentTimeMillis();
                networkSpeed.put("receivedBytes", totalReceivedBytes);
                networkSpeed.put("sentBytes", totalSentBytes);
                networkSpeed.put("lastReceivedBytes", lastReceivedBytes);
                networkSpeed.put("lastSentBytes", lastSentBytes);
                networkSpeed.put("lastTimestamp", lastTimestamp);
                networkSpeed.put("currentTimestamp", currentTimestamp);
                
                if (lastTimestamp > 0) {
                    double timeDiffSeconds = (currentTimestamp - lastTimestamp) / 1000.0;
                    if (timeDiffSeconds > 0) {
                        double receivedKbps = ((totalReceivedBytes - lastReceivedBytes) * 8.0) / (timeDiffSeconds * 1000.0);
                        double sentKbps = ((totalSentBytes - lastSentBytes) * 8.0) / (timeDiffSeconds * 1000.0);
                        
                        networkSpeed.put("receivedKbps", round(receivedKbps, 2));
                        networkSpeed.put("sentKbps", round(sentKbps, 2));
                    }
                }
                
                // 更新上次数据
                lastReceivedBytes = totalReceivedBytes;
                lastSentBytes = totalSentBytes;
                lastTimestamp = currentTimestamp;
                
                networkSpeed.put("totalReceivedMB", round(totalReceivedBytes / (1024.0 * 1024.0), 2));
                networkSpeed.put("totalSentMB", round(totalSentBytes / (1024.0 * 1024.0), 2));
            }
            
            if (process != null) {
                process.waitFor();
            }
        } catch (NumberFormatException e) {
            System.err.println("解析网络速度信息时发生数字格式异常: " + e.getMessage());
            e.printStackTrace();
            networkSpeed.put("error", "无法解析网络速度信息: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("获取网络速度信息时发生异常: " + e.getMessage());
            e.printStackTrace();
            networkSpeed.put("error", "无法获取网络速度信息: " + e.getMessage());
        } finally {
            // 确保资源被正确关闭
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (Exception e) {
                System.err.println("关闭网络信息读取器时发生异常: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        return networkSpeed;
    }
}

