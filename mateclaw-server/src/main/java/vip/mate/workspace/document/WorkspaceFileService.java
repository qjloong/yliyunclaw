package vip.mate.workspace.document;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vip.mate.workspace.document.model.WorkspaceFileEntity;
import vip.mate.workspace.document.repository.WorkspaceFileMapper;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 工作区文件服务
 * <p>
 * 管理 Agent 级别的 Markdown 文档，支持启用/禁用、排序，
 * 并将启用的文件内容拼接为系统提示词。
 *
 * @author MateClaw Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkspaceFileService {

    private final WorkspaceFileMapper fileMapper;

    /**
     * 列出 Agent 的所有工作区文件（按排序 + 文件名排列）
     */
    public List<WorkspaceFileEntity> listFiles(Long agentId) {
        List<WorkspaceFileEntity> files = fileMapper.selectList(
                new LambdaQueryWrapper<WorkspaceFileEntity>()
                        .eq(WorkspaceFileEntity::getAgentId, agentId)
                        .orderByAsc(WorkspaceFileEntity::getSortOrder)
                        .orderByAsc(WorkspaceFileEntity::getFilename));
        // 返回列表时不包含 content（减少传输）
        files.forEach(f -> f.setContent(null));
        return files;
    }

    /**
     * 读取单个文件（含内容）
     */
    public WorkspaceFileEntity getFile(Long agentId, String filename) {
        return fileMapper.selectOne(
                new LambdaQueryWrapper<WorkspaceFileEntity>()
                        .eq(WorkspaceFileEntity::getAgentId, agentId)
                        .eq(WorkspaceFileEntity::getFilename, filename));
    }

    /**
     * 创建或更新文件
     */
    @Transactional
    public WorkspaceFileEntity saveFile(Long agentId, String filename, String content) {
        WorkspaceFileEntity existing = getFile(agentId, filename);
        long size = content != null ? content.getBytes(StandardCharsets.UTF_8).length : 0;

        if (existing != null) {
            existing.setContent(content);
            existing.setFileSize(size);
            fileMapper.updateById(existing);
            return existing;
        } else {
            WorkspaceFileEntity entity = new WorkspaceFileEntity();
            entity.setAgentId(agentId);
            entity.setFilename(filename);
            entity.setContent(content);
            entity.setFileSize(size);
            entity.setEnabled(false);
            entity.setSortOrder(0);
            fileMapper.insert(entity);
            return entity;
        }
    }

    /**
     * 删除文件
     */
    @Transactional
    public void deleteFile(Long agentId, String filename) {
        fileMapper.delete(
                new LambdaQueryWrapper<WorkspaceFileEntity>()
                        .eq(WorkspaceFileEntity::getAgentId, agentId)
                        .eq(WorkspaceFileEntity::getFilename, filename));
    }

    /**
     * 获取当前启用的系统提示文件名列表（有序）
     */
    public List<String> getPromptFiles(Long agentId) {
        return fileMapper.selectList(
                new LambdaQueryWrapper<WorkspaceFileEntity>()
                        .eq(WorkspaceFileEntity::getAgentId, agentId)
                        .eq(WorkspaceFileEntity::getEnabled, true)
                        .orderByAsc(WorkspaceFileEntity::getSortOrder))
                .stream()
                .map(WorkspaceFileEntity::getFilename)
                .collect(Collectors.toList());
    }

    /**
     * 设置启用的系统提示文件列表（有序）
     * <p>
     * 传入文件名列表，按顺序设置 enabled=true 和 sortOrder；
     * 不在列表中的文件设置 enabled=false。
     */
    @Transactional
    public void setPromptFiles(Long agentId, List<String> filenames) {
        List<WorkspaceFileEntity> allFiles = fileMapper.selectList(
                new LambdaQueryWrapper<WorkspaceFileEntity>()
                        .eq(WorkspaceFileEntity::getAgentId, agentId));

        for (WorkspaceFileEntity file : allFiles) {
            int index = filenames.indexOf(file.getFilename());
            if (index >= 0) {
                file.setEnabled(true);
                file.setSortOrder(index);
            } else {
                file.setEnabled(false);
                file.setSortOrder(0);
            }
            fileMapper.updateById(file);
        }
    }

    /**
     * 将启用的工作区文件拼接为系统提示词
     * <p>
     * 每个文件以 "--- {filename} ---\n{content}\n" 的格式拼接。
     * 如果没有启用的文件，返回 null。
     */
    public String buildSystemPrompt(Long agentId) {
        List<WorkspaceFileEntity> enabledFiles = fileMapper.selectList(
                new LambdaQueryWrapper<WorkspaceFileEntity>()
                        .eq(WorkspaceFileEntity::getAgentId, agentId)
                        .eq(WorkspaceFileEntity::getEnabled, true)
                        .orderByAsc(WorkspaceFileEntity::getSortOrder));

        if (enabledFiles.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (WorkspaceFileEntity file : enabledFiles) {
            if (file.getContent() != null && !file.getContent().isBlank()) {
                if (!sb.isEmpty()) {
                    sb.append("\n\n");
                }
                sb.append("--- ").append(file.getFilename()).append(" ---\n");
                sb.append(file.getContent().trim());
            }
        }
        return sb.isEmpty() ? null : sb.toString();
    }
}
