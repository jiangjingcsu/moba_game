package com.moba.common.config;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.moba.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.util.*;

@Slf4j
public class NacosConfigLoader {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    private NacosConfigLoader() {
    }

    public static Map<String, Object> load(String localYmlPath) {
        Map<String, Object> localConfig = loadYamlFromClasspath(localYmlPath);
        if (localConfig == null) {
            throw new BusinessException(500, "本地配置文件不存在: " + localYmlPath);
        }
        log.info("本地配置文件加载成功: {}", localYmlPath);

        Map<String, Object> nacosConfig = tryLoadFromNacos(localConfig);

        Map<String, Object> mergedConfig;
        if (nacosConfig != null && !nacosConfig.isEmpty()) {
            mergedConfig = deepMerge(new LinkedHashMap<>(localConfig), nacosConfig);
            log.info("配置加载完成: Nacos配置已合并到本地配置");
        } else {
            mergedConfig = new LinkedHashMap<>(localConfig);
            log.info("配置加载完成: 使用本地配置 (Nacos无配置或不可用)");
        }

        resolvePlaceholders(mergedConfig, "");

        return mergedConfig;
    }

    @SuppressWarnings("unchecked")
    private static void resolvePlaceholders(Map<String, Object> config, String prefix) {
        for (Map.Entry<String, Object> entry : config.entrySet()) {
            String fullKey = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();

            if (value instanceof String str) {
                String resolved = resolvePlaceholder(str, fullKey);
                if (resolved != str) {
                    entry.setValue(resolved);
                }
            } else if (value instanceof Map) {
                resolvePlaceholders((Map<String, Object>) value, fullKey);
            }
        }
    }

    private static String resolvePlaceholder(String value, String fullKey) {
        if (!value.startsWith("${") || !value.endsWith("}")) {
            return value;
        }
        String inner = value.substring(2, value.length() - 1);
        int colonIndex = inner.indexOf(':');
        if (colonIndex > 0) {
            String envVar = inner.substring(0, colonIndex);
            String defaultValue = inner.substring(colonIndex + 1);
            String envValue = System.getenv(envVar);
            if (envValue != null && !envValue.isEmpty()) {
                log.info("配置项 {} 从环境变量 {} 解析: {}", fullKey, envVar, envValue);
                return envValue;
            }
            if (!defaultValue.isEmpty()) {
                log.info("配置项 {} 环境变量 {} 未设置, 使用默认值: {}", fullKey, envVar, defaultValue);
                return defaultValue;
            }
            log.warn("配置项 {} 环境变量 {} 未设置且无默认值, 保留占位符", fullKey, envVar);
            return value;
        }
        String envValue = System.getenv(inner);
        if (envValue != null && !envValue.isEmpty()) {
            log.info("配置项 {} 从环境变量 {} 解析: {}", fullKey, inner, envValue);
            return envValue;
        }
        return value;
    }

    private static Map<String, Object> loadYamlFromClasspath(String path) {
        try (InputStream is = NacosConfigLoader.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) return null;
            return YAML_MAPPER.readValue(is, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            throw new BusinessException(500, "解析本地配置文件失败: " + path + ", 原因: " + e.getMessage());
        }
    }

    private static Map<String, Object> tryLoadFromNacos(Map<String, Object> localConfig) {
        String serverAddr = getNestedString(localConfig, "nacos.server-addr");
        if (serverAddr == null || serverAddr.isEmpty()) {
            log.warn("未配置 nacos.server-addr, 跳过Nacos配置加载");
            return null;
        }

        String namespace = getNestedString(localConfig, "nacos.namespace");
        String group = getNestedString(localConfig, "nacos.group", "DEFAULT_GROUP");
        String dataId = getNestedString(localConfig, "nacos.config.data-id");
        long timeoutMs = getNestedLong(localConfig, "nacos.config.timeout-ms", 5000L);
        String username = getNestedString(localConfig, "nacos.username", "nacos");
        String password = getNestedString(localConfig, "nacos.password", "nacos");

        if (dataId == null || dataId.isEmpty()) {
            log.warn("未配置 nacos.config.data-id, 跳过Nacos配置加载");
            return null;
        }

        try {
            Properties properties = new Properties();
            properties.setProperty("serverAddr", serverAddr);
            if (namespace != null && !namespace.isEmpty()) {
                properties.setProperty("namespace", namespace);
            }
            properties.setProperty("username", username);
            properties.setProperty("password", password);

            ConfigService configService = NacosFactory.createConfigService(properties);
            String configContent = configService.getConfig(dataId, group, timeoutMs);

            if (configContent != null && !configContent.isEmpty()) {
                log.info("从Nacos获取配置成功: dataId={}, group={}", dataId, group);
                Map<String, Object> nacosConfig = YAML_MAPPER.readValue(configContent,
                        new TypeReference<LinkedHashMap<String, Object>>() {});
                configService.shutDown();
                return nacosConfig;
            } else {
                log.warn("Nacos中不存在配置: dataId={}, group={}, 使用本地配置", dataId, group);
                configService.shutDown();
                return null;
            }
        } catch (NoClassDefFoundError e) {
            log.warn("nacos-client未在classpath中, 跳过Nacos配置加载");
            return null;
        } catch (NacosException e) {
            log.warn("从Nacos加载配置失败, 使用本地配置: dataId={}, 原因: {}", dataId, e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("解析Nacos配置失败, 使用本地配置: dataId={}, 原因: {}", dataId, e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepMerge(Map<String, Object> base, Map<String, Object> override) {
        for (Map.Entry<String, Object> entry : override.entrySet()) {
            String key = entry.getKey();
            Object overrideValue = entry.getValue();

            if (overrideValue instanceof Map && base.get(key) instanceof Map) {
                deepMerge((Map<String, Object>) base.get(key), (Map<String, Object>) overrideValue);
            } else {
                base.put(key, overrideValue);
            }
        }
        return base;
    }

    @SuppressWarnings("unchecked")
    public static Object getNestedValue(Map<String, Object> config, String path) {
        if (config == null || path == null) return null;
        String[] keys = path.split("\\.");
        Object current = config;
        for (String key : keys) {
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(key);
            } else {
                return null;
            }
        }
        return current;
    }

    public static String getNestedString(Map<String, Object> config, String path) {
        Object value = getNestedValue(config, path);
        return value != null ? String.valueOf(value) : null;
    }

    public static String getNestedString(Map<String, Object> config, String path, String defaultValue) {
        String value = getNestedString(config, path);
        return value != null ? value : defaultValue;
    }

    public static String getRequiredString(Map<String, Object> config, String path) {
        Object value = getNestedValue(config, path);
        if (value == null) {
            throw new BusinessException(500, "缺少必要配置项: " + path);
        }
        String str = String.valueOf(value);
        if (str.isEmpty() || str.startsWith("${")) {
            throw new BusinessException(500, "配置项未设置有效值: " + path);
        }
        return str;
    }

    public static int getRequiredInt(Map<String, Object> config, String path) {
        Object value = getNestedValue(config, path);
        if (value == null) {
            throw new BusinessException(500, "缺少必要配置项: " + path);
        }
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new BusinessException(500, "配置项不是有效的整数: " + path + "=" + value);
        }
    }

    public static long getRequiredLong(Map<String, Object> config, String path) {
        Object value = getNestedValue(config, path);
        if (value == null) {
            throw new BusinessException(500, "缺少必要配置项: " + path);
        }
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new BusinessException(500, "配置项不是有效的长整数: " + path + "=" + value);
        }
    }

    public static float getRequiredFloat(Map<String, Object> config, String path) {
        Object value = getNestedValue(config, path);
        if (value == null) {
            throw new BusinessException(500, "缺少必要配置项: " + path);
        }
        if (value instanceof Number) return ((Number) value).floatValue();
        try {
            return Float.parseFloat(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new BusinessException(500, "配置项不是有效的浮点数: " + path + "=" + value);
        }
    }

    public static boolean getRequiredBoolean(Map<String, Object> config, String path) {
        Object value = getNestedValue(config, path);
        if (value == null) {
            throw new BusinessException(500, "缺少必要配置项: " + path);
        }
        if (value instanceof Boolean) return (Boolean) value;
        String str = String.valueOf(value).toLowerCase();
        if ("true".equals(str) || "false".equals(str)) return Boolean.parseBoolean(str);
        throw new BusinessException(500, "配置项不是有效的布尔值: " + path + "=" + value);
    }

    public static int getNestedInt(Map<String, Object> config, String path, int defaultValue) {
        Object value = getNestedValue(config, path);
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static long getNestedLong(Map<String, Object> config, String path, long defaultValue) {
        Object value = getNestedValue(config, path);
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static boolean getNestedBoolean(Map<String, Object> config, String path, boolean defaultValue) {
        Object value = getNestedValue(config, path);
        if (value == null) return defaultValue;
        if (value instanceof Boolean) return (Boolean) value;
        return Boolean.parseBoolean(String.valueOf(value));
    }

    public static float getNestedFloat(Map<String, Object> config, String path, float defaultValue) {
        Object value = getNestedValue(config, path);
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).floatValue();
        try {
            return Float.parseFloat(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T getNestedObject(Map<String, Object> config, String path, Class<T> type) {
        Object value = getNestedValue(config, path);
        if (value == null) return null;
        if (type.isInstance(value)) return (T) value;
        return null;
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> getNestedList(Map<String, Object> config, String path) {
        Object value = getNestedValue(config, path);
        if (value instanceof List) return (List<Map<String, Object>>) value;
        return null;
    }

    @SuppressWarnings("unchecked")
    public static Properties flattenToProperties(Map<String, Object> config) {
        Properties properties = new Properties();
        flattenMap("", config, properties);
        return properties;
    }

    @SuppressWarnings("unchecked")
    private static void flattenMap(String prefix, Map<String, Object> source, Properties target) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map) {
                flattenMap(key, (Map<String, Object>) value, target);
            } else if (value != null) {
                target.setProperty(key, String.valueOf(value));
            }
        }
    }
}
