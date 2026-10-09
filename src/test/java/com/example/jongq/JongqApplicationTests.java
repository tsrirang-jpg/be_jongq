package com.example.jongq;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(properties = "app.admin.password=TestAdmin123")
class JongqApplicationTests {

	@Test
	void contextLoads() {
	}

}
