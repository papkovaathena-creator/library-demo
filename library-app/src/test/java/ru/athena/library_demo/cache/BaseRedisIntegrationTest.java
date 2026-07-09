package ru.athena.library_demo.cache;

import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.junit.jupiter.Container;

@SpringBootTest
@ActiveProfiles("hsqldb")
public class BaseRedisIntegrationTest {

    @Container
    static final RedisContainer REDIS = new RedisContainer("redis:7-alpine");

    {
        REDIS.start();
    }

    static void registerProperties(DynamicPropertyRegistry registry){
        registry.add("spring.data.redis.host",REDIS::getRedisHost);
        registry.add("spring.data.redis.port",REDIS::getRedisPort);
    }
}
