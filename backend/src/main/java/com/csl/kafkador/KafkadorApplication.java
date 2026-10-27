package com.csl.kafkador;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

// No generated in-memory user: who signs in is decided by AccessService.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class KafkadorApplication {

	public static void main(String[] args) {
		SpringApplication.run(KafkadorApplication.class, args);
	}

}
