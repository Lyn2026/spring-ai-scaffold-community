package com.scaffoldai.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 演示应用：引入 starter 后即可拥有「带记忆对话 + 知识库问答 + SSE 流式 + 工具调用」。
 */
@SpringBootApplication
public class ScaffoldDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScaffoldDemoApplication.class, args);
    }
}
