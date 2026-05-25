package vip.mate.planning.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vip.mate.planning.model.PlanEntity;
import vip.mate.planning.model.SubPlanEntity;
import vip.mate.planning.repository.PlanMapper;
import vip.mate.planning.repository.SubPlanMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Collectors;

/**
 * 任务规划服务
 * 管理 Plan-and-Execute 模式下的计划和子任务
 *
 * @author MateClaw Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanningService {

    private final PlanMapper planMapper;
    private final SubPlanMapper subPlanMapper;

    /**
     * 创建执行计划（由 StateGraphPlanExecuteAgent 调用）
     */
    @Transactional
    public PlanEntity createPlan(String agentId, String conversationId, String goal, List<String> steps) {
        PlanEntity plan = new PlanEntity();
        plan.setAgentId(agentId);
        plan.setConversationId(conversationId);
        plan.setGoal(goal);
        plan.setStatus("running");
        plan.setTotalSteps(steps.size());
        plan.setCompletedSteps(0);
        planMapper.insert(plan);

        IntStream.range(0, steps.size()).forEach(i -> {
            SubPlanEntity sub = new SubPlanEntity();
            sub.setPlanId(plan.getId());
            sub.setStepIndex(i);
            sub.setDescription(steps.get(i));
            sub.setStatus("pending");
            subPlanMapper.insert(sub);
        });

        log.info("Created plan {} with {} steps for agent {} in conversation {}",
            plan.getId(), steps.size(), agentId, conversationId);
        return plan;
    }

    /**
     * 更新子计划状态
     */
    public void updateSubPlanStatus(Long planId, int stepIndex, String status) {
        SubPlanEntity sub = getSubPlan(planId, stepIndex);
        if (sub != null) {
            sub.setStatus(status);
            if ("running".equals(status)) {
                sub.setStartTime(LocalDateTime.now());
            }
            subPlanMapper.updateById(sub);
        }
    }

    /**
     * 更新子计划执行结果
     */
    public void updateSubPlanResult(Long planId, int stepIndex, String result) {
        SubPlanEntity sub = getSubPlan(planId, stepIndex);
        if (sub != null) {
            sub.setResult(result);
            sub.setStatus("completed");
            sub.setEndTime(LocalDateTime.now());
            subPlanMapper.updateById(sub);

            // 更新主计划完成步骤数
            PlanEntity plan = planMapper.selectById(planId);
            if (plan != null) {
                plan.setCompletedSteps(plan.getCompletedSteps() + 1);
                planMapper.updateById(plan);
            }
        }
    }

    /**
     * 完成计划
     */
    public void completePlan(Long planId, String summary) {
        PlanEntity plan = planMapper.selectById(planId);
        if (plan != null) {
            plan.setStatus("completed");
            plan.setSummary(summary);
            plan.setEndTime(LocalDateTime.now());
            planMapper.updateById(plan);
        }
    }

    /**
     * 获取 Agent 的计划列表
     */
    public List<PlanEntity> listPlansByAgent(String agentId) {
        return planMapper.selectList(new LambdaQueryWrapper<PlanEntity>()
                .eq(PlanEntity::getAgentId, agentId)
                .orderByDesc(PlanEntity::getCreateTime));
    }

    /**
     * 获取计划详情（含子计划）
     */
    public PlanEntity getPlanWithSteps(Long planId) {
        PlanEntity plan = planMapper.selectById(planId);
        if (plan != null) {
            List<SubPlanEntity> steps = subPlanMapper.selectList(
                    new LambdaQueryWrapper<SubPlanEntity>()
                            .eq(SubPlanEntity::getPlanId, planId)
                            .orderByAsc(SubPlanEntity::getStepIndex));
            plan.setSteps(steps);
        }
        return plan;
    }

    /**
     * 获取子计划
     */
    public List<SubPlanEntity> getSubPlans(Long planId) {
        return subPlanMapper.selectList(new LambdaQueryWrapper<SubPlanEntity>()
                .eq(SubPlanEntity::getPlanId, planId)
                .orderByAsc(SubPlanEntity::getStepIndex));
    }

    /**
     * 标记计划失败
     */
    public void markPlanFailed(Long planId, String reason) {
        PlanEntity plan = planMapper.selectById(planId);
        if (plan != null) {
            plan.setStatus("failed");
            plan.setSummary(reason);
            plan.setEndTime(LocalDateTime.now());
            planMapper.updateById(plan);
        }
    }

    /**
     * 更新子计划失败状态
     */
    public void updateSubPlanFailure(Long planId, int stepIndex, String error) {
        SubPlanEntity sub = getSubPlan(planId, stepIndex);
        if (sub != null) {
            sub.setStatus("failed");
            sub.setResult(error);
            sub.setEndTime(LocalDateTime.now());
            subPlanMapper.updateById(sub);
        }
    }

    /**
     * 审批 replay 上下文：找到最近一条 running 且含 awaiting_approval 步骤的计划，
     * 返回恢复图执行所需的全部状态。
     */
        public PlanResumeContext findAwaitingApprovalContext(String conversationId) {
        if (conversationId != null && !conversationId.isBlank()) {
            List<PlanEntity> conversationPlans = planMapper.selectList(new LambdaQueryWrapper<PlanEntity>()
                .eq(PlanEntity::getStatus, "running")
                .eq(PlanEntity::getConversationId, conversationId)
                .orderByDesc(PlanEntity::getCreateTime));
            for (PlanEntity plan : conversationPlans) {
            PlanResumeContext ctx = buildResumeContext(plan);
            if (ctx != null) {
                log.info("[PlanningService] Found awaiting-approval context by conversation: conversationId={}, planId={}, steps={}, awaitingStep={}",
                    conversationId, plan.getId(), ctx.steps().size(), ctx.awaitingStepIndex());
                return ctx;
            }
            }
        }

        // Legacy fallback for rows created before conversation_id existed.
        // Only reuse when there is exactly one awaiting-approval candidate.
        List<PlanResumeContext> candidates = planMapper.selectList(new LambdaQueryWrapper<PlanEntity>()
                .eq(PlanEntity::getStatus, "running")
                .orderByDesc(PlanEntity::getCreateTime))
            .stream()
            .map(this::buildResumeContext)
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toList());
        if (candidates.size() == 1) {
            PlanResumeContext ctx = candidates.get(0);
            log.warn("[PlanningService] Falling back to legacy global awaiting-approval plan lookup for conversation {}: planId={}",
                conversationId, ctx.planId());
            return ctx;
        }
        if (candidates.size() > 1) {
            log.warn("[PlanningService] Multiple awaiting-approval plans found without conversation match for conversation {}; refusing ambiguous replay context",
                conversationId);
        }
        return null;
    }

    /** replay 恢复上下文 DTO */
    public record PlanResumeContext(Long planId, List<String> steps, int awaitingStepIndex,
                                    List<String> completedResults) {}

        private PlanResumeContext buildResumeContext(PlanEntity plan) {
        if (plan == null) return null;

        List<SubPlanEntity> subPlans = subPlanMapper.selectList(
            new LambdaQueryWrapper<SubPlanEntity>()
                .eq(SubPlanEntity::getPlanId, plan.getId())
                .orderByAsc(SubPlanEntity::getStepIndex));

        int awaitingIndex = subPlans.stream()
            .filter(s -> "awaiting_approval".equals(s.getStatus()))
            .mapToInt(SubPlanEntity::getStepIndex)
            .findFirst()
            .orElse(-1);
        if (awaitingIndex < 0) return null;

        List<String> steps = subPlans.stream()
            .map(SubPlanEntity::getDescription)
            .collect(Collectors.toList());

        List<String> completedResults = subPlans.stream()
            .filter(s -> "completed".equals(s.getStatus()))
            .map(s -> String.format("步骤%d结果：%s", s.getStepIndex() + 1, s.getResult()))
            .collect(Collectors.toList());

        return new PlanResumeContext(plan.getId(), steps, awaitingIndex, completedResults);
        }

    private SubPlanEntity getSubPlan(Long planId, int stepIndex) {
        return subPlanMapper.selectOne(new LambdaQueryWrapper<SubPlanEntity>()
                .eq(SubPlanEntity::getPlanId, planId)
                .eq(SubPlanEntity::getStepIndex, stepIndex));
    }
}
