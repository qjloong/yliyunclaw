package vip.mate.channel.web;

import lombok.Data;

@Data
public class ChatExecutionSelection {
    private String type;
    private String key;
    private String label;
    private String source;
    private Boolean fallbackAllowed;
    private Boolean boundToAgent;

    public boolean isSkill() {
        return "skill".equalsIgnoreCase(type);
    }

    public boolean isTool() {
        return "tool".equalsIgnoreCase(type);
    }

    public boolean hasSelection() {
        return key != null && !key.isBlank();
    }
}
