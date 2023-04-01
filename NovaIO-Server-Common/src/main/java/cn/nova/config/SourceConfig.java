package cn.nova.config;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * {@link SourceConfig}作为一级配置类，读取了字符串格式的KV配置项，并提供了转换成基本数据类型的能力
 *
 * @author RealDragonking
 */
public final class SourceConfig {

    private final Map<String, String> stringMap;

    private SourceConfig() {
        this.stringMap = new HashMap<>();
    }

    /**
     * 读取文件并创建一个{@link SourceConfig}
     *
     * @return {@link SourceConfig}
     */
    public static SourceConfig init() {
        int len;
        byte[] bytes = new byte[2048];
        File configFile = new File("./config");
        SourceConfig srcConfig = new SourceConfig();

        try {
            if (configFile.createNewFile()) {
                try (InputStream source = SourceConfig.class.getResourceAsStream("/config");
                     OutputStream target = new FileOutputStream(configFile)) {
                    if (source == null) {
                        return null;
                    }
                    while ((len = source.read(bytes)) > -1) {
                        target.write(bytes, 0, len);
                    }
                }
            }

            try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
                reader.lines().forEach(line -> {
                    if (! line.startsWith("#")) {
                        String[] pair = line.split("=");
                        if (pair.length == 2) {
                            srcConfig.stringMap.put(pair[0].trim(), pair[1].trim());
                        }
                    }
                });
            }
        } catch (IOException e) {
            return null;
        }

        return srcConfig;
    }

    /**
     * 根据指定的键，获取到{@link String}类型的值
     *
     * @param key 键
     * @return {@link String}
     */
    public String getString(String key) {
        return stringMap.get(key);
    }

    /**
     * 根据指定的键，获取到int类型的值。若值为空，则返回给定的默认值
     *
     * @param key 键
     * @param defaultVal 默认值
     * @return int
     */
    public int getIntValue(String key, int defaultVal) {
        String val = stringMap.get(key);
        return val == null ? defaultVal : Integer.parseInt(val);
    }

    /**
     * 根据指定的键，获取到long类型的值。若值为空，则返回给定的默认值
     *
     * @param key 键
     * @param defaultVal 默认值
     * @return long
     */
    public long getLongValue(String key, long defaultVal) {
        String val = stringMap.get(key);
        return val == null ? defaultVal : Long.parseLong(val);
    }

    /**
     * 根据指定的键，获取到boolean类型的值。若值为空，则返回给定的默认值
     *
     * @param key 键
     * @param defaultVal 默认值
     * @return boolean
     */
    public boolean getBooleanValue(String key, boolean defaultVal) {
        String val = stringMap.get(key);
        return val == null ? defaultVal : Boolean.parseBoolean(key);
    }

}
