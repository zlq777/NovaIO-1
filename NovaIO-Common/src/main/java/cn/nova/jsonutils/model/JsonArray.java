package cn.nova.jsonutils.model;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class JsonArray implements Iterable {

    private final List list = new ArrayList();

    /**
     * @param obj 往JsonArray的List新添封装好的Json对象
     */
    public void add(Object obj) {
        list.add(obj);
    }

    /**
     * @param index
     * @return 获得当前JsonArray index下标的Json对象
     */
    public Object get(int index) {
        return list.get(index);
    }
    public <T> T get(int index,Class<T> type) {
        Object obj = list.get(index);
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
    public int size() {
        return list.size();
    }
    /**
     * @param index 想要获取Json对象键的值
     * @return 获得当前JsonArray的对应下标index的Json对象
     */
    public JsonObject getJsonObject(int index) {
        Object obj = list.get(index);
        if (!(obj instanceof JsonObject)) {
            throw new RuntimeException("Type of value is not JsonObject");
        }
        return (JsonObject) obj;
    }
    /**
     * @param index 想要获取Json对象键的值
     * @return 获得当前JsonArray的对应下标index的JsonArray对象
     */
    public JsonArray getJsonArray(int index) {
        Object obj = list.get(index);
        if (!(obj instanceof JsonArray)) {
            throw new RuntimeException("Type of value is not JsonArray");
        }
        return (JsonArray) obj;
    }

    public Iterator iterator() {
        return list.iterator();
    }
}
