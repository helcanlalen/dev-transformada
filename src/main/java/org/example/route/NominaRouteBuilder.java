package org.example.route;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.example.config.ConfigurationProvider;

/**
 * Route builder for processing nomina-related HTTP requests.
 * Handles the specific /nomina endpoint and its processing logic.
 */
@ApplicationScoped
public class NominaRouteBuilder extends KafkaToLogRoute {
    
    @Override
    protected void configureRoutes() {
        String kafkaTopicRequest = configProvider.getProperty("KAFKA_TOPIC_REQUEST");
        String cluster_port = configProvider.getClusterPort();
        String cluster = configProvider.getCluster();

        String consumer = "kafka:my-topic10-response?brokers=" + cluster + ":" + cluster_port +"&groupId=camel-group";
        
        if (kafkaTopicRequest == null || kafkaTopicRequest.isEmpty()) {
            kafkaTopicRequest = "my-topic10"; 
            System.out.println("TopicS empty, using default: " + kafkaTopicRequest);
        }
        
        final String kafkaTopic = kafkaTopicRequest;
        
        // HTTP endpoint that processes nomina requests and sends them to Kafka
        from("platform-http:/nomina")
             .routeId("http-to-kafka")
            // Generate correlation ID for request tracking
            .process(exchange -> {
                String correlationId = java.util.UUID.randomUUID().toString();
                exchange.setProperty("correlationId", correlationId);
                exchange.getMessage().setHeader("correlationId", correlationId);
                System.out.println("HTTP Received, correlation ID: " + correlationId);
            })
            // Transform the input using JSLT
            .to("jslt:classpath:transformationInput.jslt")
            .log("Transformed2 JSON: ${body}")
            
            //Send to Kafka topic
            .to("kafka:" + kafkaTopicRequest +"?brokers=cluster-nonprod01-kafka-bootstrap.amq-streams-kafka:9092")
            .log("✅ Sent to Kafka topic " + kafkaTopicRequest)
        
            //Using a dynamic SEDA endpoint based on correlation ID
            .process(exchange -> {
                String correlationId = exchange.getProperty("correlationId", String.class);
                exchange.getContext().getRegistry().bind(correlationId, exchange.getIn().getBody());
            })
            .pollEnrich()
            .simple("seda:waitForKafkaResponse-${exchangeProperty.correlationId}?timeout=2000")
                        .aggregationStrategy((oldExchange, newExchange) -> {
                    if (newExchange == null) {
                        oldExchange.getMessage().setBody("{\"error\": \"Request timed out waiting for Kafka response\"}");
                        oldExchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 504); // Gateway Timeout
                        return oldExchange;
                    }
                    return newExchange;
                })
            .log("✅ Returning response to HTTP caller: ${body}");

        // Kafka consumer that listens for responses and forwards them to the waiting HTTP request
        from(consumer)
            .routeId("kafka-response-consumer")
            .log("📥 Received from my-topic10-response: ${body} with correlationId=${header.correlationId}")
            .process(exchange -> {
                // Get the correlation ID from the Kafka message
                String correlationId = exchange.getMessage().getHeader("correlationId", String.class);
                String responseBody = exchange.getMessage().getBody(String.class);
                
                if (correlationId != null) {
                    // ISSUE: The response wasn't being correlated with the request
                    // FIXED: Using correlation ID to send to the specific SEDA endpoint
                    exchange.getContext().createProducerTemplate()
                        .sendBody("seda:waitForKafkaResponse-" + correlationId, responseBody);
                    
                    log.info("Forwarded response to endpoint for correlationId: {}", correlationId);
                } else {
                    log.warn("Received Kafka message without correlation ID: {}", responseBody);
                }
            });
    }
}
