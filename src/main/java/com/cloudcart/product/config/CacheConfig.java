package com.cloudcart.product.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis Cache Configuration for CloudCart Product Service.
 *
 * Cache Strategy:
 *   - "products"  : list of all products / per-category lists  (TTL: 5 min)
 *   - "product"   : single product by ID                        (TTL: 5 min)
 *
 * Graceful Fallback:
 *   If Redis is unavailable at startup, a NoOpCacheManager is used so the
 *   service continues serving data directly from PostgreSQL.
 *
 * Future Event-Driven Readiness:
 *   Cache eviction currently happens via @CacheEvict annotations on mutations.
 *   When a message broker (RabbitMQ/Kafka) is introduced, eviction can be moved
 *   to event listeners (e.g., on ProductUpdatedEvent) with zero controller changes.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        try {
            // Verify connectivity before committing to Redis
            connectionFactory.getConnection().ping();

            RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                    .entryTtl(DEFAULT_TTL)
                    .disableCachingNullValues()
                    .serializeKeysWith(
                            RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()));

            log.info("Redis cache manager initialized (TTL={})", DEFAULT_TTL);

            return RedisCacheManager.builder(connectionFactory)
                    .cacheDefaults(config)
                    .build();

        } catch (Exception e) {
            log.warn("Redis unavailable — falling back to NoOpCacheManager. Products will be served directly from DB. Error: {}",
                    e.getMessage());
            return new NoOpCacheManager();
        }
    }
}
