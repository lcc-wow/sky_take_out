package com.sky;

import com.sky.controller.admin.EmployeeController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableTransactionManagement //开启注解方式的事务管理
public class SkyApplication {
    public static void main(String[] args) {
        final Logger log = LoggerFactory.getLogger(SkyApplication.class);
        SpringApplication.run(SkyApplication.class, args);
        log.info("server started");
    }
}
