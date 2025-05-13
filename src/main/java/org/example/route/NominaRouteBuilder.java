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
        from("platform-http:/nomina")
            .routeId("http-to-kafka")
            // Generate correlation ID for request tracking
            .process(this::addCorrelationId)
            // Transform the input using JSLT
            .to("jslt:classpath:transformationInput.jslt")
            .log("Transformed JSON: ${body}")
            
            // Send to Kafka topic
            .to("kafka:" + kafkaTopic + "?brokers=" + kafkaBrokers)
            .log("✅ Sent to Kafka topic `" + kafkaTopic + "`")
        
            // Wait for response from Kafka via SEDA
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
    }
}
