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
    String kafkaTopicRequest;
    String kafkaTopicRequestPlanilla;
    String port; 
    String cluster;
    String cluster_port;
    
    public ConfigurationProvider() {
        properties = new Properties();

        // Lectura variables de enterno
        kafkaTopicRequest = System.getenv("KAFKA_TOPIC_REQUEST");
        kafkaTopicRequestPlanilla = System.getenv("KAFKA_TOPIC_REQUEST_PLANILLA");
        port = System.getenv("PORT");
        cluster = System.getenv("CLUSTER");
        cluster_port = System.getenv("CLUSTER_PORT");
        // Mostrar los valores para depuración
        System.out.println("Configuración cargada desde variables de entorno:");
        System.out.println("kafka_topic_request = " + kafkaTopicRequest);
        System.out.println("kafka_topic_request_planilla = " + kafkaTopicRequestPlanilla);
        System.out.println("port = " + port);
        System.out.println("cluster = " + cluster);        
        System.out.println("cluster_port = " + cluster_port);
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
    
    public String getCluster() {
        return properties.getProperty(this.cluster);
    }    
    
    public String getClusterPort() {
        return properties.getProperty(this.cluster_port);
    }
}
