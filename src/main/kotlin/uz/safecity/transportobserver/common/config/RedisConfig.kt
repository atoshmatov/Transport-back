package uz.safecity.transportobserver.common.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.StringRedisSerializer

/**
 * Redis is used for:
 *  - refresh token storage / revocation (auth module)
 *  - short-lived caches (map/live locations, dashboards)
 */
@Configuration
class RedisConfig {

	@Bean
	fun redisTemplate(connectionFactory: RedisConnectionFactory): RedisTemplate<String, String> {
		val template = RedisTemplate<String, String>()
		template.connectionFactory = connectionFactory
		template.keySerializer = StringRedisSerializer()
		template.valueSerializer = StringRedisSerializer()
		template.hashKeySerializer = StringRedisSerializer()
		template.hashValueSerializer = StringRedisSerializer()
		return template
	}
}
