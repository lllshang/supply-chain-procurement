package com.dzgylxt.common;

import com.dzgylxt.vo.common.ImportTaskVO;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内任务登记表：保存异步导入/导出任务的进度与产物。
 *
 * <p>P1 为单机内存实现，进程重启后任务丢失；后续接入分布式任务/对象存储时可替换本组件，
 * 调用方（Service/Controller）无需改动。</p>
 */
@Component
public class TaskRegistry {

    private final Map<String, ImportTaskVO> tasks = new ConcurrentHashMap<>();
    private final Map<String, byte[]> artifacts = new ConcurrentHashMap<>();

    /** 生成任务 id。 */
    public String nextId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public void putTask(ImportTaskVO task) {
        if (task != null && task.getTaskId() != null) {
            tasks.put(task.getTaskId(), task);
        }
    }

    public ImportTaskVO getTask(String taskId) {
        return taskId == null ? null : tasks.get(taskId);
    }

    public void putArtifact(String taskId, byte[] bytes) {
        if (taskId != null && bytes != null) {
            artifacts.put(taskId, bytes);
        }
    }

    public byte[] getArtifact(String taskId) {
        return taskId == null ? null : artifacts.get(taskId);
    }
}
