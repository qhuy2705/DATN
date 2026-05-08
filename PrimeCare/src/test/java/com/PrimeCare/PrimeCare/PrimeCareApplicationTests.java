package com.PrimeCare.PrimeCare;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

class PrimeCareApplicationTests {

	@Test
	void applicationEntrypointIsSpringBootApplication() {
		assertThat(PrimeCareApplication.class).hasAnnotation(SpringBootApplication.class);
	}

}
