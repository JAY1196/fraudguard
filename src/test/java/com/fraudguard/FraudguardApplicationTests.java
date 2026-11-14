package com.fraudguard;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import({TestcontainersConfiguration.class, PostgresTestcontainersConfiguration.class})
class FraudguardApplicationTests {

	@Test
	void contextLoads() {
	}

}
