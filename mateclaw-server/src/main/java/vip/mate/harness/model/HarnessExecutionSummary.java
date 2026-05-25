package vip.mate.harness.model;

import lombok.Data;

@Data
public class HarnessExecutionSummary {
    private int promptTokens;
    private int completionTokens;
    private String runtimeModelName;
    private String runtimeProviderId;
    private String finishReason;
    private String errorMessage;
    private String finalAnswerPreview;
}
