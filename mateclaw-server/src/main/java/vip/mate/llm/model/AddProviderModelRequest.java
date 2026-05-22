package vip.mate.llm.model;

import lombok.Data;

@Data
public class AddProviderModelRequest {
    private String id;
    private String name;
    /** chat（默认） / embedding */
    private String modelType;
}
