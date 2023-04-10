package cn.nova.jsonutils.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JsonObject {

    private final Map<String, Object> map = new HashMap<String, Object>();

    public void put(String key, Object value) {
        map.put(key, value);
    }

    public Object get(String key) {
        return map.get(key);
    }
    public<T> T get(String key,Class<T> type) {
        Object obj = map.get(key);
        if (type.isInstance(obj)) {
            return type.cast(obj);
        } else {
            try {
                return type.cast(obj);
            } catch (ClassCastException e) {
                throw new IllegalArgumentException("无法将对象 " + obj + " 转换为类型 " + type.getName());
            }
        }
    }
    public List<Map.Entry<String, Object>> getAllKeyValue() {
        return new ArrayList<>(map.entrySet());
    }

    /**
     * @param key 想要获取JsonObject对象键的值
     * @return 获得当前JsonObject的对应键key的JsonObject对象
     */
    public JsonObject getJsonObject(String key) {
        if (!map.containsKey(key)) {
            throw new IllegalArgumentException("Invalid key");
        }

        Object obj = map.get(key);
        if (!(obj instanceof JsonObject)) {
            throw new RuntimeException("Type of value is not JsonObject");
        }

        return (JsonObject) obj;
    }
    /**
     * @param key 想要获取JsonArray对象键的值
     * @return 获得当前JsonObject的对应键key的JsonArray对象
     */
    public JsonArray getJsonArray(String key) {
        if (!map.containsKey(key)) {
            throw new IllegalArgumentException("Invalid key");
        }

        Object obj = map.get(key);
        if (!(obj instanceof JsonArray)) {
            throw new RuntimeException("Type of value is not JsonArray");
        }

        return (JsonArray) obj;
    }

}
