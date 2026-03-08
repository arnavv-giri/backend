package com.thriftbazaar.backend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

@SpringBootApplication
public class BackendApplication {

	private static final Logger log = LoggerFactory.getLogger(BackendApplication.class);

	public static void main(String[] args) {
		ConfigurableApplicationContext ctx = SpringApplication.run(BackendApplication.class, args);
		Environment env = ctx.getEnvironment();

		log.info("=================================================");
		log.info("ThriftBazaar API started successfully");
		log.info("  Port          : {}", env.getProperty("server.port", "8081"));
		log.info("  Database URL  : {}", maskPassword(env.getProperty("spring.datasource.url", "N/A")));
		log.info("  CORS origin   : {}", env.getProperty("app.cors.allowed-origin", "N/A"));
		log.info("  JWT expiry    : {} ms", env.getProperty("jwt.expiration-ms", "3600000"));
		log.info("=================================================");
	}

	/**
	 * Strips any password= fragment from a JDBC URL before logging it.
	 * Purely defensive — passwords should not be in JDBC URLs, but this
	 * ensures nothing sensitive ever reaches the log file.
	 */
	private static String maskPassword(String url) {
		if (url == null) return "N/A";
		return url.replaceAll("(?i)(password=)[^&;]*", "$1***");
	}
}
