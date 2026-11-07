package com.scaffoldai.tool;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个方法为可被模型调用的工具。方法所在 Bean 会被脚手架自动扫描注册。
 * <p>
 * 参数名依赖编译期 -parameters，本脚手架已在父 POM 中开启。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AiTool {

    /**
     * 工具名，模型看到的名字。默认用方法名。
     */
    String name() default "";

    /**
     * 工具用途说明，直接影响模型是否会正确调用，务必写清楚何时使用。
     */
    String description() default "";
}
