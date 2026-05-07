package ru.athena.library_demo;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("hsqldb")
@Disabled
class LibraryDemoApplicationTests {

	@Test
	void contextLoads() {
	}

}
