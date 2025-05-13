package org.example.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.ConfigProvider;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import java.io.File; 

@ApplicationScoped
public class ConfigurationProvider {
    
    private final Properties properties;
    
    public ConfigurationProvider() {
        properties = new Properties();
        try {
            // Lectura variables de enterno
            String kafkaTopicRequest = System.getenv("KAFKA_TOPIC_REQUEST");
            String kafkaTopicRequestPlanilla = System.getenv("KAFKA_TOPIC_REQUEST_PLANILLA");
            String port = System.getenv("PORT");

            // Mostrar los valores para depuración
            System.out.println("Configuración cargada desde variables de entorno:");
            System.out.println("kafka_topic_request = " + kafkaTopicRequest);
            System.out.println("kafka_topic_request_planilla = " + kafkaTopicRequestPlanilla);
            System.out.println("port = " + port);
            
        } catch (IOException e) {
            System.err.println("No se puden cargar las variables de entorno: " + e.getMessage());
        }
    }
    
    @Produces
    @Singleton
    @Named("customProperties")
    public Properties getProperties() {
        return properties;
    }
    
    //Obtener cualquier propiedad por su nombre
    public String getProperty(String propertyName) {
        return properties.getProperty(propertyName);
    }

}
