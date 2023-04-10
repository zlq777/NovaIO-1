package cn.nova.network;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 用于方法和类的注解，被{@link PathMapping}标注的方法可以被{@link MsgHandler}扫描注册，标注的类可以提供前缀path
 *
 * @author RealDragonking
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface PathMapping {

    /**
     * 消息处理路由，相当于一个命名空间
     *
     * @return 消息处理路由
     */
    String path();

}
