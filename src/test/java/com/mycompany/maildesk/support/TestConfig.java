package com.mycompany.maildesk.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import com.mycompany.maildesk.user.UserService;

@TestConfiguration
public class TestConfig {

    @Bean
    public TestData testData(JdbcTemplate jdbc, UserService users) {
        return new TestData(jdbc, users);
    }
}
