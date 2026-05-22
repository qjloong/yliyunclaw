package vip.mate.workspace.core.model;

import lombok.Data;

/**
 * Git-scoped changed file summary for the currently located project.
 */
@Data
public class ProjectChangeSummaryItem {
    private String path;
    private String changeType;
}