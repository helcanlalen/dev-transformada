package org.example.route;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.example.config.ConfigurationProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
/**
 * Route builder for processing nomina-related HTTP requests.
 * Handles the specific /Loan-Retail (Nomina/planilla/libre disponiblidad).
 */
@ApplicationScoped
public class NominaRouteBuilder extends KafkaToLogRoute {
    
    @Override
    protected void configureRoutes() {

        String kafkaTopicRequest = configProvider.getTopicRequestNomina();
        String cluster_port = configProvider.getClusterPort();
        String cluster = configProvider.getCluster();
        
        String consumer = "kafka:my-topic10-response?brokers=" + cluster + ":" + cluster_port +"&groupId=camel-group";
        
        if (kafkaTopicRequest == null || kafkaTopicRequest.isEmpty()) {
            kafkaTopicRequest = "my-topic10"; 
            System.out.println("Topic144 empty, using default: " + kafkaTopicRequest);
        }
    
        // HTTP endpoint that processes nomina requests and sends them to Kafka
        from("platform-http:/Loan-Retail")
            .routeId("http-to-kafka")
            // Generate correlation ID for request tracking
            .process(exchange -> {
                
                try{
                    String jsltFile = ""; 
                    String correlationId = java.util.UUID.randomUUID().toString();
                    ObjectMapper mapper = new ObjectMapper();
                    
                    String transformedBody = exchange.getMessage().getBody(String.class);
                    JsonNode rootNode = mapper.readTree(transformedBody);
                    JsonNode bodyNode = rootNode.get("body");
                    String productId = "";
                    
                    exchange.setProperty("correlationId", correlationId);
                    exchange.getMessage().setHeader("correlationId", correlationId);
                    System.out.println("HTTP Received, correlation ID: " + correlationId);
                        
                    // Bussqueda productId en Json
                    if (bodyNode != null && bodyNode.has("productId")) {
                        productId = bodyNode.get("productId").asText();
                        System.out.println("productId (desde body): " + productId);
                    } else {
                        System.out.println("No se pudo encontrar el productId en el JSON");
                    }
                        
                    if (configProvider.getProductIdNomina().equals(productId)) {
                        jsltFile = "transformationInputNomina.jslt";
                    } else if (configProvider.getproductIdLibreDisp().equals(productId)) {
                        jsltFile = "transformadaInputLibreDisp.jslt";
                    } 
                    
                    exchange.setProperty("jsltFile", jsltFile);
                            
                } catch (IOException e) {
                    System.err.println("Error al parsear JSON: " + e.getMessage());
                    exchange.setException(e);
                } catch (Exception e) {
                    System.err.println("Error inesperado: " + e.getMessage());
                    e.printStackTrace();
                    exchange.setException(e);
                }

            })
            //.to("jslt:classpath:transformationInputNomina.jslt") utilzar cuando se envie a un jslt en especifico
            .toD("jslt:classpath:${exchangeProperty.jsltFile}")
            .log("Transformed JSON: ${body}")
            
            // Send to Kafka topic
            .to("kafka:" + kafkaTopicRequest +"?brokers=" + cluster + ":" + cluster_port)

            .log("Sent to Kafka topic `my-topic10`")
        
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
                    return newExchange;
                })
            .log("Returning response to HTTP caller: ${body}");

        // Kafka consumer that listens for responses and forwards them to the waiting HTTP request
        from("kafka:my-topic10-response?brokers=" + cluster + ":" + cluster_port +"&groupId=camel-group")
            .routeId("kafka-response-consumer")
            .log("Received from my-topic10-response: ${body} with correlationId=${header.correlationId}")
            .process(exchange -> {
                // Get the correlation ID from the Kafka message
                String correlationId = exchange.getMessage().getHeader("correlationId", String.class);
                String responseBody = exchange.getMessage().getBody(String.class);
                
                if (correlationId != null) {
                    // FIXED: Using correlation ID to send to the specific endpoint
                    exchange.getContext().createProducerTemplate()
                        .sendBody("seda:waitForKafkaResponse-" + correlationId, responseBody);
                    
                    log.info("Forwarded response to endpoint for correlationId: {}", correlationId);
                } else {
                    log.warn("Received Kafka message without correlation ID: {}", responseBody);
                }
            });
    }
}
