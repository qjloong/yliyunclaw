package vip.mate.workspace.core.model;

import lombok.Data;

import java.util.List;

/**
 * 项目理解缓存摘要。
 */
@Data
public class ProjectInsightSummary {
    private String projectName;
    private String rootPath;
    private String relativePath;
    private String workingDirectoryPath;
    private String workingDirectoryRelativePath;
    private String locatorType;
    private List<String> locatorMarkers;
    private List<String> stackHints;
    private List<String> keyFiles;
    private List<String> moduleHints;
    /**
     * Cached compact directory/material index hints for prompt routing.
     * Built once per project insight cache entry and reused across turns.
     */
    private List<String> materialIndexHints;
    private List<String> commandHints;
    private String packageManager;
    private String buildSystem;
    private String gitRootPath;
    private String gitRootRelativePath;
    private Integer changedFileCount;
    private List<ProjectChangeSummaryItem> changedFiles;
    private String lastScannedAt;
}
