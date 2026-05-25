package vip.mate.template.contract;

/**
 * Canonical sections of a template contract, separating metadata from runtime
 * behavior, safety defaults, acceptance declarations, and materialization instructions (WP-6).
 *
 * <p>This enum addresses WP-0 ambiguity {@code A09} (template authority blur) by making
 * the <em>intent</em> of each template field group explicit.
 *
 * @author MateClaw Team
 * @see TemplateFieldClassification
 * @see TemplateApplicationContract
 */
public enum TemplateSection {

    /** Descriptive metadata: id, name, version, category, domain, icon, description. */
    IDENTITY_METADATA,

    /** Runtime behavior hints: preferredMode, allowedModes, behavior suggestions. */
    RUNTIME_DEFAULTS,

    /** Safety policy defaults: sandboxMode, approvalPolicy, networkPolicy. */
    SAFETY_DEFAULTS,

    /** Context routing declarations: knowledgeBindings, contextSources. */
    CONTEXT_DECLARATIONS,

    /** Workspace materialization: workspaceFiles, defaultKnowledgeBases. */
    MATERIALIZATION,

    /** Acceptance and quality declarations: qualityGates, mockAcceptanceTasks, mvpScope. */
    ACCEPTANCE_CONTRACT,

    /** Capability and tool surface declarations: tools, capabilityPack. */
    CAPABILITY_DECLARATION,

    /** UX and onboarding metadata: homeQuickStarts, interactionHints, starterPrompts. */
    UX_METADATA
}
