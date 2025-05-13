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
        String kafkaTopicRequest = configProvider.getProperty("kafka_topic_request");
        if (kafkaTopicRequest == null || kafkaTopicRequest.isEmpty()) {
            kafkaTopicRequest = "my-topic10"; // Default value if configuration loading fails
            System.out.println("Topic empty, using default: " + kafkaTopicRequest);
        }
        
        final String kafkaTopic = kafkaTopicRequest;
        
        // HTTP endpoint that processes nomina requests and sends them to Kafka
        // HTTP endpoint that processes requests and sends them to Kafka
        from("platform-http:/nomina")
            .routeId("http-to-kafka")
            // Generate correlation ID for request tracking
            .process(exchange -> {
                String correlationId = java.util.UUID.randomUUID().toString();
                exchange.setProperty("correlationId", correlationId);
                exchange.getMessage().setHeader("correlationId", correlationId);
                System.out.println("🔹 HTTP Received. Correlation ID: " + correlationId);
            })
            // Transform the input using JSLT
            .to("jslt:classpath:transformationInput.jslt")
            .log("Transformed2 JSON: ${body}")
            
            // ISSUE: The InOnly exchange pattern prevents the route from waiting for a response
            // FIXED: Removed this pattern to allow request-reply pattern to work
            // .setExchangePattern(org.apache.camel.ExchangePattern.InOnly) 
            
            // Send to Kafka topic
            .to("kafka:" + kafkaTopicRequest +"?brokers=cluster-nonprod01-kafka-bootstrap.amq-streams-kafka:9092")

            .log("✅ Sent to Kafka topic `my-topic10`")
        
            // Wait for response from Kafka via SEDA
            // ISSUE: The SEDA component should be using the correlation ID to match requests and responses
            // FIXED: Using a dynamic SEDA endpoint based on correlation ID
            .process(exchange -> {
                // Store original exchange in a registry or cache with correlation ID as key
                String correlationId = exchange.getProperty("correlationId", String.class);
                exchange.getContext().getRegistry().bind(correlationId, exchange.getIn().getBody());
            })
            .pollEnrich()
            .simple("seda:waitForKafkaResponse-${exchangeProperty.correlationId}?timeout=2000")
                        .aggregationStrategy((oldExchange, newExchange) -> {
                    if (newExchange == null) {
                        // Handle timeout case
                        oldExchange.getMessage().setBody("{\"error\": \"Request timed out waiting for Kafka response\"}");
                        oldExchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 504); // Gateway Timeout
                        return oldExchange;
                    }
                    // Return the response received from Kafka
                    return newExchange;
                })
            .log("✅ Returning response to HTTP caller: ${body}");

        // Kafka consumer that listens for responses and forwards them to the waiting HTTP request
        from("kafka:my-topic10-response?brokers=cluster-nonprod01-kafka-bootstrap.amq-streams-kafka:9092&groupId=camel-group")
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
                    
                    log.info("📤 Forwarded response to SEDA endpoint for correlationId: {}", correlationId);
                } else {
                    log.warn("⚠️ Received Kafka message without correlation ID: {}", responseBody);
                }
            });
    }
}
