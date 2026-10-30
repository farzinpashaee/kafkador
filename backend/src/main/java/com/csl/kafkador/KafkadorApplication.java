package com.csl.kafkador;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.ldap.LdapAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

// No generated in-memory user: who signs in is decided by AccessService.
// No default LDAP connection: LdapAuthenticator builds one from the saved settings. Spring Boot's would point at
// localhost:389, and its health check would mark Kafkador DOWN whenever nothing answers there.
@SpringBootApplication(exclude = {UserDetailsServiceAutoConfiguration.class, LdapAutoConfiguration.class})
public class KafkadorApplication {

	public static void main(String[] args) {
		SpringApplication.run(KafkadorApplication.class, args);
	}

}
