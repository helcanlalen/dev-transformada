package org.example.config;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.ConfigProvider;
import java.util.Properties;

@ApplicationScoped
public class ConfigurationProvider {
    
    String kafkaTopicRequest;
    String kafkaTopicRequestNomina;
    String port; 
    String cluster;
    String cluster_port;
    String productIdNomina;
    String productIdLibreDisp;
    
    public ConfigurationProvider() {
        
        //Variables de entorno
        this.kafkaTopicRequest = System.getenv("KAFKA_TOPIC_REQUEST");
        this.kafkaTopicRequestNomina = System.getenv("KAFKA_TOPIC_REQUEST_RETAIL");
        this.port = System.getenv("PORT");
        this.cluster = System.getenv("CLUSTER");
        this.cluster_port = System.getenv("CLUSTER_PORT");
        this.productIdNomina = System.getenv("ID_PROD_NOMINA");
        this.productIdLibreDisp = System.getenv("ID_PROD_LIB_DISP");

        System.out.println("Configuracion cargada desde variables de entorno:");
        System.out.println("kafka_topic_request = " + kafkaTopicRequest);
        System.out.println("kafka_topic_request_Nomina = " + kafkaTopicRequestNomina);
        System.out.println("port = " + port);
        System.out.println("cluster = " + cluster);        
        System.out.println("cluster_port = " + cluster_port);
        System.out.println("Product ID nomina = " + productIdNomina);        
        System.out.println("Product ID libre disponibilidad= " + productIdLibreDisp);
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
