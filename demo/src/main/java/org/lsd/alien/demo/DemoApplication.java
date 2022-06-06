package org.lsd.alien.demo;

import javax.sql.DataSource;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableConfigurationProperties({DataSourceProperties.class, AlienDataSourceWrapper.class})
@AutoConfigureBefore(DataSourceAutoConfiguration.class)
@EnableTransactionManagement
@MapperScan(basePackages = {"org.lsd.alien.demo.dao"})
@ComponentScan(basePackages = {"org.lsd.alien"})
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @Bean(initMethod = "init")
    @ConditionalOnMissingBean
    @Primary
    public DataSource dataSource() {
        return new AlienDataSourceWrapper();
    }
}
