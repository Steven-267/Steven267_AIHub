package com.steven.ai;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot应用程序主启动类
 * 通过@SpringBootApplication注解标记为Spring Boot应用的入口
 * @MapperScan注解用于指定MyBatis的Mapper接口所在包路径
 */
@MapperScan("com.steven.ai.mapper") // 扫描指定包下的MyBatis Mapper接口
@SpringBootApplication // 标记为Spring Boot应用程序的主启动类
public class StevenAiApplication {

    /**
     * 应用程序主方法
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        // 启动Spring Boot应用程序
        SpringApplication.run(StevenAiApplication.class, args);
    }

}

