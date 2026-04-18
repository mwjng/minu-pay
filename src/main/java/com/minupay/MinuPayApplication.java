package com.minupay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class MinuPayApplication {

	public static void main(String[] args) {
		SpringApplication.run(MinuPayApplication.class, args);
	}

}
