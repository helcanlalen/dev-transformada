package org.example.route;

import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;

import org.apache.camel.builder.RouteBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.component.jackson.JacksonDataFormat;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import jakarta.inject.Inject;
import org.example.config.ConfigurationProvider;
/**
 * Camel route that processes HTTP requests, forwards them to Kafka, and waits for responses.
 * The route implements a request-reply pattern using correlation IDs.
 */
@ApplicationScoped
public class KafkaToLogRoute extends RouteBuilder {

    @Inject
    ConfigurationProvider configProvider;

    /**
     * Creates an SSL context that trusts all certificates without verification.
     * SECURITY WARNING: This should only be used in development/testing environments.
     * In production, proper certificate validation should be implemented.
     */
    @Produces
    @Named("sslContextParameters")
    public SSLContextParameters createSslContextParameters() {
        SSLContextParameters sslContextParameters = new SSLContextParameters();
        
        // Create a trust manager that accepts all certificates without validation
        TrustManagersParameters trustManagersParameters = new TrustManagersParameters();
        trustManagersParameters.setTrustManager(new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] chain, String authType) {}
            public void checkServerTrusted(X509Certificate[] chain, String authType) {}
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        });
        sslContextParameters.setTrustManagers(trustManagersParameters);
        
        return sslContextParameters;
    }

    @Override
    public void configure() throws Exception {

        // Obtén cualquier propiedad dinámicamente por su nombre
        String kafkaTopicRequest = configProvider.getProperty("kafka_topic_request");
        String port = configProvider.getProperty("port");

        // Configure JSON data format
        @SuppressWarnings("resource")
        JacksonDataFormat jsonDataFormat = new JacksonDataFormat();
        jsonDataFormat.setPrettyPrint(false);
    
        // Configure REST component
        restConfiguration()
            .component("platform-http")
            .port(8080);
    
        // Global exception handler
        onException(Exception.class)
            .handled(true)
            .logStackTrace(true)
            .log(LoggingLevel.ERROR, "Error processing message: ${exception.message}")
            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(500))
            .setBody(simple("{\"error\": \"${exception.message}\"}"));
            
        // HTTP endpoint that processes requests and sends them to Kafka
        from("platform-http:/test")
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
