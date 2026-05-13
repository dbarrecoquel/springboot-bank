package com.bank.infrastructure.messaging;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;
import org.springframework.kafka.listener.ContainerProperties;
import com.bank.domain.event.FraudAlertEvent;
import com.bank.domain.event.TransactionCreatedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@Configuration
@EnableKafka
public class KafkaConfig {

	public static final String TOPIC_TRANSACTIONS = "banking.transactions";
	
	public static final String TOPIC_FRAUD_ALERTS = "banking.fraud-alerts";
	
	public static final String TOPIC_ACCOUNT_EVENTS = "banking.account-events";
	
	public static final String TOPIC_NOTIFICATIONS = "banking.notifications";
	
	public static final String GROUP_FRAUD_DETECTION = "fraud-detection-group";
	
	public static final String GROUP_NOTIFICATIONS = "notification-group";
	
	public static final String GROUP_AUDIT = "audit-group";
	
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;
 
    @Value("${spring.kafka.consumer.auto-offset-reset:earliest}")
    private String autoOffsetReset;
 
    @Value("${banking.kafka.producer.retries:3}")
    private int producerRetries;
 
    @Value("${banking.kafka.producer.acks:all}")
    private String producerAcks;
    
    @Bean
    public ObjectMapper kafkaObjectMapper() {
    	ObjectMapper mapper = new ObjectMapper();
    	mapper.registerModule(new JavaTimeModule());
    	mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    	return mapper;
    }


    // ─────────────────────────────────────────────────────────
    //  Topics — création automatique au démarrage
    // ─────────────────────────────────────────────────────────
    @Bean
    public KafkaAdmin.NewTopics bankingTopics() {
    	
    	return new KafkaAdmin.NewTopics(
    			topicTransactions(),
    			topicFraudAlerts(),
    			topicAccountEvents(),
    			topicNotifications(),
                TopicBuilder.name(TOPIC_TRANSACTIONS  + ".DLT").partitions(1).replicas(1).build(),
                TopicBuilder.name(TOPIC_FRAUD_ALERTS  + ".DLT").partitions(1).replicas(1).build(),
                TopicBuilder.name(TOPIC_NOTIFICATIONS + ".DLT").partitions(1).replicas(1).build()

    			);
    }
    private NewTopic topicTransactions() {
        return TopicBuilder.name(TOPIC_TRANSACTIONS)
            .partitions(3)   // partitionnement par accountId — parallélisme × 3
            .replicas(1)     // 1 en dev, 3 en prod
            .config("retention.ms", String.valueOf(7 * 24 * 60 * 60 * 1000L)) // 7 jours
            .build();
    }
    private NewTopic topicFraudAlerts() {
        return TopicBuilder.name(TOPIC_FRAUD_ALERTS)
            .partitions(1)   // 1 partition — ordre strict des alertes garanti
            .replicas(1)
            .config("retention.ms", String.valueOf(30L * 24 * 60 * 60 * 1000L)) // 30 jours
            .build();
    }
    private NewTopic topicAccountEvents() {
        return TopicBuilder.name(TOPIC_ACCOUNT_EVENTS)
            .partitions(3)
            .replicas(1)
            .config("retention.ms", String.valueOf(30L * 24 * 60 * 60 * 1000L))
            .build();
    }
    private NewTopic topicNotifications() {
        return TopicBuilder.name(TOPIC_NOTIFICATIONS)
            .partitions(2)
            .replicas(1)
            .config("retention.ms", String.valueOf(3 * 24 * 60 * 60 * 1000L)) // 3 jours
            .build();
    }
    
    // ─────────────────────────────────────────────────────────
    //  Producer — configuration commune
    // ─────────────────────────────────────────────────────────
 
    private Map<String, Object> producerProps() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        // Durabilité : attendre l'accusé de réception de tous les replicas (acks=all)
        props.put(ProducerConfig.ACKS_CONFIG, producerAcks);
        props.put(ProducerConfig.RETRIES_CONFIG, producerRetries);
        // Idempotence — évite les doublons en cas de retry réseau
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        // Compression — réduit le débit réseau
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");
        // Batching — optimise le débit
        props.put(ProducerConfig.LINGER_MS_CONFIG, 5);
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        return props;
    }

    @Bean
    public ProducerFactory<String, Object> producerFactory(ObjectMapper kafka) {
    	
    	DefaultKafkaProducerFactory<String, Object> factory = new DefaultKafkaProducerFactory<>(producerProps());
    	factory.setValueSerializer(new JsonSerializer<>(kafkaObjectMapper()));
    	
    	return factory;
    }
    
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
    	return new KafkaTemplate<>(producerFactory);
    }
    
    // ─────────────────────────────────────────────────────────
    //  Consumer — configuration commune
    // ─────────────────────────────────────────────────────────
 
    private Map<String, Object> consumerProps(String groupId) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,  bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG,           groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,  autoOffsetReset);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,   StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        // Commit manuel — offset validé uniquement après traitement réussi
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        // Limite de messages par poll — contrôle la charge par batch
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG,   10);
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300_000); // 5 min max
        return props;
    }
    
    // ─────────────────────────────────────────────────────────
    //  Consumer Factories — une par type d'événement
    // ─────────────────────────────────────────────────────────
 
    @Bean
    public ConsumerFactory<String, TransactionCreatedEvent> transactionConsumerFactory(ObjectMapper kafkaObjectMapper) {
    	
    	JsonDeserializer<TransactionCreatedEvent> deser= new JsonDeserializer<>(TransactionCreatedEvent.class, kafkaObjectMapper, false);
    	
    	return new DefaultKafkaConsumerFactory<>(consumerProps(GROUP_FRAUD_DETECTION), new StringDeserializer(), deser);
    }
    
    @Bean
    public ConsumerFactory<String, FraudAlertEvent> fraudAlertConsumerFactory(
            ObjectMapper kafkaObjectMapper) {
        JsonDeserializer<FraudAlertEvent> deser = new JsonDeserializer<>(FraudAlertEvent.class, kafkaObjectMapper, false);
        return new DefaultKafkaConsumerFactory<>(
            consumerProps(GROUP_FRAUD_DETECTION),
            new StringDeserializer(), deser);
    }
    @Bean
    public ConsumerFactory<String, Object> notificationConsumerFactory(
            ObjectMapper kafkaObjectMapper) {
        JsonDeserializer<Object> deser =
            new JsonDeserializer<>(Object.class, kafkaObjectMapper, false);
        deser.addTrustedPackages("com.bank.domain.event");
        return new DefaultKafkaConsumerFactory<>(
            consumerProps(GROUP_NOTIFICATIONS),
            new StringDeserializer(), deser);
    }

    // ─────────────────────────────────────────────────────────
    //  Listener Container Factories
    // ─────────────────────────────────────────────────────────
 
    /**
     * Factory pour les consommateurs d'événements de transaction.
     * Mode MANUAL_IMMEDIATE : commit de l'offset uniquement après succès.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TransactionCreatedEvent> transactionListenerContainerFactory(ConsumerFactory<String, TransactionCreatedEvent> transactionConsumerFactory, KafkaTemplate<String, Object> kafkaTemplate) {
    	  return buildFactory(transactionConsumerFactory, kafkaTemplate);
    }
    
    /**
     * Factory pour les consommateurs d'alertes fraude.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, FraudAlertEvent>
    fraudAlertListenerContainerFactory(
            ConsumerFactory<String, FraudAlertEvent> fraudAlertConsumerFactory,
            KafkaTemplate<String, Object> kafkaTemplate) {
 
        return buildFactory(fraudAlertConsumerFactory, kafkaTemplate);
    }

    
    /**
     * Factory générique pour les consommateurs de notifications.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object>
    notificationListenerContainerFactory(
            ConsumerFactory<String, Object> notificationConsumerFactory,
            KafkaTemplate<String, Object> kafkaTemplate) {
 
        return buildFactory(notificationConsumerFactory, kafkaTemplate);
    }
    
    /**
     * Stratégie d'erreur commune :
     * 3 tentatives espacées de 2 secondes, puis envoi dans le DLT.
     */
    private DefaultErrorHandler buildErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer =
            new DeadLetterPublishingRecoverer(kafkaTemplate);
 
        // 3 tentatives max, intervalle fixe de 2 000 ms
        FixedBackOff backOff = new FixedBackOff(2_000L, 3L);
 
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
 
        // Ne pas retenter sur les erreurs de désérialisation — inutile
        handler.addNotRetryableExceptions(
            com.fasterxml.jackson.core.JsonProcessingException.class,
            IllegalArgumentException.class
        );
 
        return handler;
    }
 
    private <K, V> ConcurrentKafkaListenerContainerFactory<K, V> buildFactory(
            ConsumerFactory<K, V> consumerFactory,
            KafkaTemplate<String, Object> kafkaTemplate) {
 
        ConcurrentKafkaListenerContainerFactory<K, V> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
 
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setConcurrency(2); // 2 threads consommateurs par partition
        factory.setCommonErrorHandler(buildErrorHandler(kafkaTemplate));
 
        return factory;
    }

}
