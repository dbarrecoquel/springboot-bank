package com.bank.infrastructure;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;


@SpringBootApplication(scanBasePackages = "com.bank")
@EnableJpaRepositories(basePackages = "com.bank.infrastructure.persistence")
@EntityScan(basePackages = "com.bank.domain.entity")
public class InfrastructureTestApplication {}