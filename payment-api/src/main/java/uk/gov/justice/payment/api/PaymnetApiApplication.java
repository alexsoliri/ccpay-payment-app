package uk.gov.justice.payment.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;
import java.io.IOException;


@SpringBootApplication
public class PaymnetApiApplication {

	private static final Logger logger = LoggerFactory
			.getLogger(PaymnetApiApplication.class);


	public static void main(String[] args) {
		logger.debug("Payment API Started");
		System.out.println("Hello");

        SpringApplication.run(PaymnetApiApplication.class, args);
	}
}
