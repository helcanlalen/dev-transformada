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
    String kafkaTopicRequestNomina;
    String port; 
    String cluster;
    String cluster_port;
    String productIdNomina;
    String productIdLibreDisp;
    
    public ConfigurationProvider() {
        properties = new Properties();

        //Variables de entorno
        kafkaTopicRequest = System.getenv("KAFKA_TOPIC_REQUEST");
        kafkaTopicRequestNomina = System.getenv("KAFKA_TOPIC_REQUEST_RETAIL");
        port = System.getenv("PORT");
        cluster = System.getenv("CLUSTER");
        cluster_port = System.getenv("CLUSTER_PORT");
        productIdNomina = System.getenv("ID_PROD_NOMINA");
        productIdLibreDisp = System.getenv("ID_PROD_LIB_DISP");

        System.out.println("Configuracion cargada desde variables de entorno:");
        System.out.println("kafka_topic_request = " + kafkaTopicRequest);
        System.out.println("kafka_topic_request_Nomina = " + kafkaTopicRequestNomina);
        System.out.println("port = " + port);
        System.out.println("cluster = " + cluster);        
        System.out.println("cluster_port = " + cluster_port);
        System.out.println("Product ID nomina = " + productIdNomina);        
        System.out.println("Product ID libre disponibilidad= " + productIdLibreDisp);
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

    public String getTopicRequestNomina(){
        return this.kafkaTopicRequestNomina;
    }
    
    public String getCluster() {
        return this.cluster;
    }    
    
    public String getClusterPort() {
        return this.cluster_port;
    }

    public String getProductIdNomina() {
        return this.productIdNomina;
    }    
    
    public String getproductIdLibreDisp() {
        return this.productIdLibreDisp;
    }
}
