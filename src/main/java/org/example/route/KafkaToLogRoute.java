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
public abstract class KafkaToLogRoute extends RouteBuilder {

    @Inject
    ConfigurationProvider configProvider;
    protected JacksonDataFormat jsonDataFormat;
    protected String kafkaBrokers;

    @Override
    public void configure() throws Exception {
        // Initialize common configurations
        initCommonConfig();
        
        // Set up global exception handling
        configureExceptionHandling();
        
        // Configure the REST component
        configureRestComponent();
        
        // Configure route-specific endpoints and logic
        configureRoutes();
    }
       
    /**
     * Inicializacion cluster
     */
    protected void initCommonConfig() {
        // Initialize JSON data format
        jsonDataFormat = new JacksonDataFormat();
        jsonDataFormat.setPrettyPrint(false);
        
        kafkaBrokers = "cluster-nonprod01-kafka-bootstrap.amq-streams-kafka:9092";
    }

    /**
     * Configuracion global exception handler
     */
    protected void configureExceptionHandling() {
        onException(Exception.class)
            .handled(true)
            .logStackTrace(true)
            .log(LoggingLevel.ERROR, "Error processing message: ${exception.message}")
            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(500))
            .setBody(simple("{\"error\": \"${exception.message}\"}"));
    }

    /**
     * Configuracion rest del componente
     */
    protected void configureRestComponent() {
        String port = configProvider.getProperty("port");
        
        restConfiguration()
            .component("platform-http")
            .port(port);
    }

    /**
     * Creacion del correlativo ID y agregarlo al exchange
     * @param exchange The exchange to process
     */
    protected void addCorrelationId(Exchange exchange) {
        String correlationId = java.util.UUID.randomUUID().toString();
        exchange.setProperty("correlationId", correlationId);
        exchange.getMessage().setHeader("correlationId", correlationId);
        System.out.println("🔹 JEJE HTTP Received. Correlation ID: " + correlationId);
    }

    /**
     * Implement this method to define the specific routes for each implementation
     */
    protected abstract void configureRoutes();
}
