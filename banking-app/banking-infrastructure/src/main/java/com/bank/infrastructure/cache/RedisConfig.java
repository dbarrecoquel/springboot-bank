package com.bank.infrastructure.cache;

import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
@Configuration
@EnableCaching
public class RedisConfig {

	//noms de caches Spring (@Cacheable)
	
	public static final String CACHE_ACCOUNTS = "accounts";
	public static final String CACHE_USERS = "users";
	public static final String CACHE_EXCHANGE_RATES = "exchange-rates";
    public static final String CACHE_TRANSACTION_LIMITS = "transaction-limits";
    public static final String CACHE_FRAUD_SCORES       = "fraud-scores";
 
    // ── Préfixes de clés Redis (accès direct) ────────────────
    public static final String PREFIX_SESSION           = "session:";
    public static final String PREFIX_RATE              = "rate:";
    public static final String PREFIX_RATE_TIMESTAMP    = "rate:updated:";
    public static final String PREFIX_VELOCITY          = "velocity:";
    public static final String PREFIX_OTP               = "otp:";
    public static final String PREFIX_TOKEN_BLACKLIST   = "jwt:blacklist:";
 
    @Value("${spring.data.redis.timeout:2000ms}")
    private String redisTimeout;

    /**
     * ObjectMapper configuré pour la sérialisation Redis.
     * Active les informations de type pour la désérialisation polymorphique.
     */
    @Bean(name = "redisObjectMapper")
    public ObjectMapper redisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // Inclure le type Java dans le JSON pour permettre la désérialisation
        mapper.activateDefaultTyping(
            LaissezFaireSubTypeValidator.instance,
            ObjectMapper.DefaultTyping.NON_FINAL,
            JsonTypeInfo.As.PROPERTY
        );
        return mapper;
    }
    /**
     * Template principal — clés String, valeurs JSON sérialisées.
     * Utilisé par {@link SessionCacheService} et {@link RateCacheService}.
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory, ObjectMapper redisObjectMapper) {
    	
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
 
        StringRedisSerializer keySerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer valueSerializer =
            new GenericJackson2JsonRedisSerializer(redisObjectMapper);
 
        template.setKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        template.setHashKeySerializer(keySerializer);
        template.setHashValueSerializer(valueSerializer);
        template.setDefaultSerializer(valueSerializer);
        template.setEnableTransactionSupport(false); // géré par Spring @Transactional
        template.afterPropertiesSet();
 
        return template;

    }
    

    /**
     * Template String simple — pour les opérations atomiques sur valeurs scalaires
     * (compteurs, flags, OTP, blacklist JWT).
     */
    @Bean
    public RedisTemplate<String, String> stringRedisTemplate(
            RedisConnectionFactory connectionFactory) {
 
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
 
        StringRedisSerializer serializer = new StringRedisSerializer();
        template.setKeySerializer(serializer);
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(serializer);
        template.setHashValueSerializer(serializer);
        template.afterPropertiesSet();
 
        return template;
    }
    
    @Bean
    public CacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            ObjectMapper redisObjectMapper) {
 
        GenericJackson2JsonRedisSerializer serializer =
            new GenericJackson2JsonRedisSerializer(redisObjectMapper);
 
        // Configuration par défaut — TTL 5 min, null non mis en cache
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration
            .defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(5))
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair
                    .fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair
                    .fromSerializer(serializer))
            .disableCachingNullValues()
            .prefixCacheNameWith("banking:");  // toutes les clés préfixées "banking:<cache>:"
 
        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigurations(defaultConfig))
            .transactionAware()
            .build();
    }
    /**
     * TTL par cache — surcharge la valeur par défaut (5 min).
     */
    private Map<String, RedisCacheConfiguration> cacheConfigurations(
            RedisCacheConfiguration base) {
 
        return Map.of(
            CACHE_ACCOUNTS,
                base.entryTtl(Duration.ofMinutes(5)),
 
            CACHE_USERS,
                base.entryTtl(Duration.ofMinutes(10)),
 
            CACHE_EXCHANGE_RATES,
                base.entryTtl(Duration.ofHours(1)),
 
            CACHE_TRANSACTION_LIMITS,
                base.entryTtl(Duration.ofHours(1)),
 
            CACHE_FRAUD_SCORES,
                base.entryTtl(Duration.ofMinutes(30))
        );
    }

}
