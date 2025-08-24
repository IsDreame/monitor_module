package cn.nebulaedata.cccs.acutor_module.config;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DockerLogWebSocketHandler extends TextWebSocketHandler {
    private final Map<String, Process> logProcesses = new ConcurrentHashMap<>();
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        try {
            String containerId = getContainerIdFromSession(session);
            System.out.println("尝试建立到容器 " + containerId + " 的日志连接");
            
            if (containerId != null) {
                sessions.put(session.getId(), session);
                
                // 启动Docker日志进程
                String[] command = {"docker", "logs", "-f", "--tail", "50", containerId};
                System.out.println("执行命令: " + String.join(" ", command));
                
                Process process = Runtime.getRuntime().exec(command);
                logProcesses.put(session.getId(), process);
                
                // 启动线程读取日志输出
                Thread logReaderThread = new Thread(() -> {
                    BufferedReader reader = null;
                    try {
                        reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                        System.out.println("开始读取容器 " + containerId + " 的日志");
                        
                        String line;
                        while ((line = reader.readLine()) != null && sessions.containsKey(session.getId())) {
                            if (session.isOpen()) {
                                session.sendMessage(new TextMessage(line));
                            }
                        }
                        System.out.println("完成读取容器 " + containerId + " 的日志");
                    } catch (Exception e) {
                        System.err.println("读取Docker日志时发生异常: " + e.getMessage());
                        e.printStackTrace();
                        
                        // 尝试通知客户端发生了错误
                        try {
                            if (session.isOpen()) {
                                String errorMsg = "服务器内部错误: " + e.getMessage();
                                // 特别处理权限相关的异常
                                if (e.getMessage().contains("Permission denied") || 
                                    e.getMessage().contains("permission denied")) {
                                    errorMsg += "\n💡 权限被拒绝。请确保容器已正确配置Docker权限。";
                                }
                                session.sendMessage(new TextMessage(errorMsg));
                            }
                        } catch (Exception ex) {
                            System.err.println("发送错误消息到客户端时发生异常: " + ex.getMessage());
                            ex.printStackTrace();
                        }
                    } finally {
                        if (reader != null) {
                            try {
                                reader.close();
                            } catch (Exception e) {
                                System.err.println("关闭日志读取器时发生异常: " + e.getMessage());
                                e.printStackTrace();
                            }
                        }
                    }
                });
                logReaderThread.setDaemon(true);
                logReaderThread.start();
                
                // 启动线程读取错误输出
                Thread errorReaderThread = new Thread(() -> {
                    BufferedReader errorReader = null;
                    try {
                        errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                        String line;
                        StringBuilder errorOutput = new StringBuilder();
                        
                        while ((line = errorReader.readLine()) != null) {
                            errorOutput.append(line).append("\n");
                        }
                        
                        if (errorOutput.length() > 0) {
                            System.err.println("Docker命令错误输出: " + errorOutput.toString());
                            // 发送错误信息到客户端
                            if (session.isOpen()) {
                                String errorMsg = "Docker错误: " + errorOutput.toString();
                                // 特别处理权限问题
                                if (errorOutput.toString().contains("permission denied") || 
                                    errorOutput.toString().contains("access denied") ||
                                    errorOutput.toString().contains("Are you trying to connect to a TLS-enabled daemon without TLS?")) {
                                    errorMsg += "\n💡 权限被拒绝。请确保容器已正确配置Docker权限。";
                                }
                                session.sendMessage(new TextMessage(errorMsg));
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("读取Docker错误输出时发生异常: " + e.getMessage());
                        e.printStackTrace();
                    } finally {
                        if (errorReader != null) {
                            try {
                                errorReader.close();
                            } catch (Exception e) {
                                System.err.println("关闭错误输出读取器时发生异常: " + e.getMessage());
                                e.printStackTrace();
                            }
                        }
                    }
                });
                errorReaderThread.setDaemon(true);
                errorReaderThread.start();
            } else {
                System.err.println("无法从会话中获取有效的容器ID");
                session.sendMessage(new TextMessage("错误: 无法获取容器ID"));
            }
        } catch (Exception e) {
            System.err.println("建立WebSocket连接时发生异常: " + e.getMessage());
            e.printStackTrace();
            
            // 尝试通知客户端发生了错误
            try {
                if (session.isOpen()) {
                    String errorMsg = "服务器内部错误: " + e.getMessage();
                    // 特别处理权限相关的异常
                    if (e.getMessage().contains("Permission denied") || 
                        e.getMessage().contains("permission denied")) {
                        errorMsg += "\n💡 权限被拒绝。请确保容器已正确配置Docker权限。";
                    }
                    session.sendMessage(new TextMessage(errorMsg));
                }
            } catch (Exception ex) {
                System.err.println("发送错误消息到客户端时发生异常: " + ex.getMessage());
                ex.printStackTrace();
            }
        }
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        try {
            System.out.println("WebSocket连接已关闭: " + status.getCode() + " - " + status.getReason());
            sessions.remove(session.getId());
            
            // 停止Docker日志进程
            Process process = logProcesses.remove(session.getId());
            if (process != null && process.isAlive()) {
                System.out.println("终止Docker日志进程");
                process.destroyForcibly();
            }
        } catch (Exception e) {
            System.err.println("关闭WebSocket连接时发生异常: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private String getContainerIdFromSession(WebSocketSession session) {
        try {
            String query = session.getUri().getQuery();
            System.out.println("WebSocket会话查询参数: " + query);
            
            if (query != null && query.contains("containerId=")) {
                String[] params = query.split("&");
                for (String param : params) {
                    if (param.startsWith("containerId=")) {
                        String containerId = param.substring("containerId=".length());
                        System.out.println("提取到容器ID: " + containerId);
                        return containerId;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("从WebSocket会话中获取容器ID时发生异常: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }
}