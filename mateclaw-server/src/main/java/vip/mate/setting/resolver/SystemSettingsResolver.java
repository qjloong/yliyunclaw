package vip.mate.setting.resolver;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vip.mate.setting.contract.EffectiveSettingsContract;
import vip.mate.setting.contract.SettingFieldClass;
import vip.mate.setting.contract.SettingMergeStrategy;
import vip.mate.setting.contract.SettingSource;
import vip.mate.system.model.SystemSettingsDTO;
import vip.mate.system.service.SystemSettingService;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * WP-1 resolver that surfaces server-global managed settings through the canonical
 * {@link EffectiveSettingsContract} read-only model.
 *
 * <p>This component wraps {@link SystemSettingService} without altering its behavior.
 * It classifies each known setting key by its observed functional role, making the
 * distinction between ordinary product settings and safety-adjacent switches explicit
 * for later packages.
 *
 * <p>Current classification rules (first-pass):
 * <ul>
 *   <li>Debug / state-graph toggles → {@link SettingFieldClass#PRODUCT_SETTING}</li>
 *   <li>Provider selection (search, video, image, etc.) → {@link SettingFieldClass#PRODUCT_SETTING}</li>
 *   <li>Feature enablement switches → {@link SettingFieldClass#PRODUCT_SETTING}</li>
 *   <li>No keys are classified as {@code SAFETY_POLICY} at the global-system level today,
 *       because workspace policy already owns the safety authority surface.</li>
 * </ul>
 *
 * @author MateClaw Team
 * @see WorkspaceSettingsResolver
 */
@Component
@RequiredArgsConstructor
public class SystemSettingsResolver {

    private final SystemSettingService systemSettingService;

    /**
     * Resolve all known system settings as a classified map.
     *
     * @return a read-only map keyed by setting key; values carry source, class, and merge strategy metadata
     */
    public Map<String, EffectiveSettingsContract.ResolutionResult<?>> resolveSystemSettings() {
        SystemSettingsDTO dto = systemSettingService.getSettings();
        Map<String, EffectiveSettingsContract.ResolutionResult<?>> result = new LinkedHashMap<>();

        putIfNotNull(result, "language", dto.getLanguage(), SettingFieldClass.PRODUCT_SETTING);
        putBoolean(result, "streamEnabled", dto.getStreamEnabled(), SettingFieldClass.PRODUCT_SETTING);
        putBoolean(result, "debugMode", dto.getDebugMode(), SettingFieldClass.PRODUCT_SETTING);
        putBoolean(result, "stateGraphEnabled", dto.getStateGraphEnabled(), SettingFieldClass.PRODUCT_SETTING);

        putBoolean(result, "searchEnabled", dto.getSearchEnabled(), SettingFieldClass.PRODUCT_SETTING);
        putIfNotNull(result, "searchProvider", dto.getSearchProvider(), SettingFieldClass.PRODUCT_SETTING);
        putBoolean(result, "searchFallbackEnabled", dto.getSearchFallbackEnabled(), SettingFieldClass.PRODUCT_SETTING);
        putIfNotNull(result, "searxngBaseUrl", dto.getSearxngBaseUrl(), SettingFieldClass.PRODUCT_SETTING);

        putBoolean(result, "videoEnabled", dto.getVideoEnabled(), SettingFieldClass.PRODUCT_SETTING);
        putIfNotNull(result, "videoProvider", dto.getVideoProvider(), SettingFieldClass.PRODUCT_SETTING);
        putBoolean(result, "videoFallbackEnabled", dto.getVideoFallbackEnabled(), SettingFieldClass.PRODUCT_SETTING);

        putBoolean(result, "imageEnabled", dto.getImageEnabled(), SettingFieldClass.PRODUCT_SETTING);
        putIfNotNull(result, "imageProvider", dto.getImageProvider(), SettingFieldClass.PRODUCT_SETTING);
        putBoolean(result, "imageFallbackEnabled", dto.getImageFallbackEnabled(), SettingFieldClass.PRODUCT_SETTING);

        putBoolean(result, "model3dEnabled", dto.getModel3dEnabled(), SettingFieldClass.PRODUCT_SETTING);
        putIfNotNull(result, "model3dProvider", dto.getModel3dProvider(), SettingFieldClass.PRODUCT_SETTING);
        putBoolean(result, "model3dFallbackEnabled", dto.getModel3dFallbackEnabled(), SettingFieldClass.PRODUCT_SETTING);

        putBoolean(result, "ttsEnabled", dto.getTtsEnabled(), SettingFieldClass.PRODUCT_SETTING);
        putIfNotNull(result, "ttsProvider", dto.getTtsProvider(), SettingFieldClass.PRODUCT_SETTING);
        putBoolean(result, "ttsFallbackEnabled", dto.getTtsFallbackEnabled(), SettingFieldClass.PRODUCT_SETTING);
        putIfNotNull(result, "ttsAutoMode", dto.getTtsAutoMode(), SettingFieldClass.PRODUCT_SETTING);
        putIfNotNull(result, "ttsDefaultVoice", dto.getTtsDefaultVoice(), SettingFieldClass.PRODUCT_SETTING);

        putBoolean(result, "sttEnabled", dto.getSttEnabled(), SettingFieldClass.PRODUCT_SETTING);
        putIfNotNull(result, "sttProvider", dto.getSttProvider(), SettingFieldClass.PRODUCT_SETTING);
        putBoolean(result, "sttFallbackEnabled", dto.getSttFallbackEnabled(), SettingFieldClass.PRODUCT_SETTING);

        putBoolean(result, "musicEnabled", dto.getMusicEnabled(), SettingFieldClass.PRODUCT_SETTING);
        putIfNotNull(result, "musicProvider", dto.getMusicProvider(), SettingFieldClass.PRODUCT_SETTING);
        putBoolean(result, "musicFallbackEnabled", dto.getMusicFallbackEnabled(), SettingFieldClass.PRODUCT_SETTING);

        return Collections.unmodifiableMap(result);
    }

    private void putIfNotNull(Map<String, EffectiveSettingsContract.ResolutionResult<?>> map,
                              String key, String value, SettingFieldClass fieldClass) {
        map.put(key, new EffectiveSettingsContract.ResolutionResult<>(
                value,
                SettingSource.MANAGED_SYSTEM_SETTINGS,
                fieldClass,
                SettingMergeStrategy.SCALAR_OVERRIDE,
                "SystemSettingService.getSettings()"));
    }

    private void putBoolean(Map<String, EffectiveSettingsContract.ResolutionResult<?>> map,
                            String key, Boolean value, SettingFieldClass fieldClass) {
        map.put(key, new EffectiveSettingsContract.ResolutionResult<>(
                value,
                SettingSource.MANAGED_SYSTEM_SETTINGS,
                fieldClass,
                SettingMergeStrategy.SCALAR_OVERRIDE,
                "SystemSettingService.getSettings()"));
    }
}
