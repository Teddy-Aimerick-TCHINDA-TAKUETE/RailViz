package com.railviz;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class RailvizApplication {

	public static void main(String[] args) {
		SpringApplication.run(RailvizApplication.class, args);
	}
}
