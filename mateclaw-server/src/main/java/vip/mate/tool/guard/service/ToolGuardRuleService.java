package vip.mate.tool.guard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vip.mate.tool.guard.engine.ToolGuardRuleRegistry;
import vip.mate.tool.guard.model.ToolGuardRuleEntity;
import vip.mate.tool.guard.repository.ToolGuardRuleMapper;

/**
 * 工具安全规则 CRUD 服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolGuardRuleService {

    private final ToolGuardRuleMapper ruleMapper;
    private final ToolGuardRuleRegistry ruleRegistry;

    /**
     * 分页查询规则
     */
    public IPage<ToolGuardRuleEntity> listRules(int page, int size,
                                                 Boolean builtin, Boolean enabled,
                                                 String category, String severity) {
        LambdaQueryWrapper<ToolGuardRuleEntity> wrapper = new LambdaQueryWrapper<>();
        if (builtin != null) {
            wrapper.eq(ToolGuardRuleEntity::getBuiltin, builtin);
        }
        if (enabled != null) {
            wrapper.eq(ToolGuardRuleEntity::getEnabled, enabled);
        }
        if (category != null && !category.isBlank()) {
            wrapper.eq(ToolGuardRuleEntity::getCategory, category);
        }
        if (severity != null && !severity.isBlank()) {
            wrapper.eq(ToolGuardRuleEntity::getSeverity, severity);
        }
        wrapper.orderByDesc(ToolGuardRuleEntity::getPriority);
        return ruleMapper.selectPage(new Page<>(page, size), wrapper);
    }

    /**
     * 查询所有内置规则
     */
    public IPage<ToolGuardRuleEntity> listBuiltinRules(int page, int size) {
        return listRules(page, size, true, null, null, null);
    }

    /**
     * 按 ruleId 查询
     */
    public ToolGuardRuleEntity getByRuleId(String ruleId) {
        return ruleMapper.selectOne(
                new LambdaQueryWrapper<ToolGuardRuleEntity>()
                        .eq(ToolGuardRuleEntity::getRuleId, ruleId));
    }

    /**
     * 新增自定义规则
     */
    public ToolGuardRuleEntity createRule(ToolGuardRuleEntity rule) {
        rule.setBuiltin(false);
        ruleMapper.insert(rule);
        ruleRegistry.reload();
        return rule;
    }

    /**
     * 更新规则
     */
    public ToolGuardRuleEntity updateRule(String ruleId, ToolGuardRuleEntity update) {
        ToolGuardRuleEntity existing = getByRuleId(ruleId);
        if (existing == null) {
            throw new IllegalArgumentException("Rule not found: " + ruleId);
        }

        if (update.getName() != null) existing.setName(update.getName());
        if (update.getDescription() != null) existing.setDescription(update.getDescription());
        if (update.getToolName() != null) existing.setToolName(update.getToolName());
        if (update.getParamName() != null) existing.setParamName(update.getParamName());
        if (update.getCategory() != null) existing.setCategory(update.getCategory());
        if (update.getSeverity() != null) existing.setSeverity(update.getSeverity());
        if (update.getDecision() != null) existing.setDecision(update.getDecision());
        if (update.getPattern() != null) existing.setPattern(update.getPattern());
        if (update.getExcludePattern() != null) existing.setExcludePattern(update.getExcludePattern());
        if (update.getRemediation() != null) existing.setRemediation(update.getRemediation());
        if (update.getEnabled() != null) existing.setEnabled(update.getEnabled());
        if (update.getPriority() != null) existing.setPriority(update.getPriority());

        ruleMapper.updateById(existing);
        ruleRegistry.reload();
        return existing;
    }

    /**
     * 启用/禁用规则
     */
    public void toggleRule(String ruleId, boolean enabled) {
        ToolGuardRuleEntity existing = getByRuleId(ruleId);
        if (existing == null) {
            throw new IllegalArgumentException("Rule not found: " + ruleId);
        }
        existing.setEnabled(enabled);
        ruleMapper.updateById(existing);
        ruleRegistry.reload();
    }

    /**
     * 删除自定义规则（内置规则不允许删除）
     */
    public void deleteRule(String ruleId) {
        ToolGuardRuleEntity existing = getByRuleId(ruleId);
        if (existing == null) {
            throw new IllegalArgumentException("Rule not found: " + ruleId);
        }
        if (Boolean.TRUE.equals(existing.getBuiltin())) {
            throw new IllegalArgumentException("Cannot delete builtin rule: " + ruleId);
        }
        ruleMapper.deleteById(existing.getId());
        ruleRegistry.reload();
    }
}
