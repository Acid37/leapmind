package com.treepeople.leapmindtts;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;


@SpringBootApplication
@EnableAsync
@EnableEurekaServer
@EnableScheduling  //定时任务
@MapperScan("com.treepeople.leapmindtts.mapper")
public class LeapMindTtsApplication {

    public static void main(String[] args) {
        SpringApplication.run(LeapMindTtsApplication.class, args);
    }



}
