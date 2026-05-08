package com.PrimeCare.PrimeCare;

import com.PrimeCare.PrimeCare.config.MailSenderProperties;
import com.PrimeCare.PrimeCare.config.PublicLookupOtpProperties;
import com.PrimeCare.PrimeCare.config.S3Properties;
import com.PrimeCare.PrimeCare.config.SmsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties({
		S3Properties.class,
		SmsProperties.class,
		PublicLookupOtpProperties.class,
		MailSenderProperties.class
})
public class PrimeCareApplication {
	public static void main(String[] args) {
		SpringApplication.run(PrimeCareApplication.class, args);
	}

}
