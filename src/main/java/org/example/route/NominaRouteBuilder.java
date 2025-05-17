package org.example.route;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.example.config.ConfigurationProvider;

/**
 * Route builder for processing nomina-related HTTP requests.
 * Handles the specific /Loan-Retail (Nomina/planilla/libre disponiblidad).
 */
@ApplicationScoped
public class NominaRouteBuilder extends KafkaToLogRoute {
    
    /*@Override
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
                    
                    exchange.setProperty("correlationId", correlationId);
                    exchange.getMessage().setHeader("correlationId", correlationId);
                    System.out.println("HTTP Received, correlation ID: " + correlationId);

                    String productId = getProductId(exchange);
                        
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
    }*/

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
                    
                    exchange.setProperty("correlationId", correlationId);
                    exchange.getMessage().setHeader("correlationId", correlationId);
                    System.out.println("HTTP Received, correlation ID: " + correlationId);

                    String productId = getProductId(exchange);
                        
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
            
            // Verificar si hay errores de validación antes de continuar
            .choice()
                .when(exchange -> exchange.getException() != null)
                    .process(exchange -> {
                        Exception ex = exchange.getException();
                        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 400);
                        exchange.getMessage().setBody("{\"error\": \"" + ex.getMessage() + "\"}");
                        exchange.setException(null); // Limpia la excepción para que Camel no la maneje de nuevo
                    })
                    .stop() // Detener el procesamiento aquí
                .otherwise()
                    // Continuar con el flujo normal si no hay errores
                    .toD("jslt:classpath:${exchangeProperty.jsltFile}")
                    .log("Transformed JSON: ${body}")
            .end()

            // Verificar si hay errores HTTP de la API
            .choice()
                .when(exchangeProperty("httpError").isEqualTo(true))
                    .process(exchange -> {
                        int statusCode = exchange.getProperty("httpErrorStatus", Integer.class);
                        String errorBody = exchange.getProperty("httpErrorBody", String.class);
                        
                        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, statusCode);
                        exchange.getMessage().setBody(errorBody);
                    })
                    .log("Devolviendo error HTTP ${header.CamelHttpResponseCode}: ${body}")
                    .stop() // Detener el procesamiento aquí
                .otherwise()
                    // Continuar con el envío a Kafka si no hay errores
                    // Send to Kafka topic
                    .to("kafka:" + kafkaTopicRequest +"?brokers=" + cluster + ":" + cluster_port)
                    .log("Sent to Kafka topic `my-topic10`")
            .end()

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
                
                // Verificar si la respuesta de Kafka contiene un error
                String responseBody = newExchange.getMessage().getBody(String.class);
                if (responseBody != null && responseBody.contains("\"error\":")) {
                    try {
                        // Intentar determinar si hay un código de error específico en la respuesta
                        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        com.fasterxml.jackson.databind.JsonNode responseJson = mapper.readTree(responseBody);
                        
                        if (responseJson.has("statusCode")) {
                            int statusCode = responseJson.get("statusCode").asInt(500);
                            newExchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, statusCode);
                        } else {
                            // Si no hay código específico, usar 500 como predeterminado
                            newExchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 500);
                        }
                    } catch (Exception e) {
                        // Si hay algún problema al parsear, usar código 500
                        newExchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 500);
                    }
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
                    // Verificar si el mensaje de respuesta contiene información de error
                    try {
                        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        com.fasterxml.jackson.databind.JsonNode responseJson = mapper.readTree(responseBody);
                        
                        // Si hay un error en la respuesta, establecer la propiedad para manejarlo en la ruta principal
                        if (responseJson.has("error")) {
                            exchange.setProperty("hasError", true);
                            
                            // Si hay un código de estado, guardarlo
                            if (responseJson.has("statusCode")) {
                                exchange.setProperty("errorStatusCode", responseJson.get("statusCode").asInt());
                            }
                        }
                    } catch (Exception e) {
                        // Si hay un problema analizando el JSON, registrar pero continuar
                        log.warn("Error parsing response JSON: {}", e.getMessage());
                    }
                    
                    // FIXED: Using correlation ID to send to the specific endpoint
                    exchange.getContext().createProducerTemplate()
                        .sendBody("seda:waitForKafkaResponse-" + correlationId, responseBody);
                    
                    log.info("Forwarded response to endpoint for correlationId: {}", correlationId);
                } else {
                    log.warn("Received Kafka message without correlation ID: {}", responseBody);
                }
            });

                // Ruta adicional para manejar errores generales
            from("direct:handleError")
            .routeId("error-handler")
            .process(exchange -> {
                Exception ex = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                String errorMessage = ex != null ? ex.getMessage() : "Unknown error";
                int statusCode = exchange.getProperty("errorStatusCode", 500, Integer.class);
                
                exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, statusCode);
                exchange.getMessage().setBody("{\"error\": \"" + errorMessage + "\"}");
            })
            .log("Error handled: ${body}");
    }
      /**
     * Método para manejar un error HTTP y propagarlo en la respuesta
     */
    private void handleHttpError(Exchange exchange, int statusCode, String errorMsg) {
        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, statusCode);
        exchange.getMessage().setBody("{\"error\": \"" + errorMsg + "\"}");
    }
}
