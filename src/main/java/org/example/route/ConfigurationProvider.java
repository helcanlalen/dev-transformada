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
            // Lee el archivo de la raíz del proyecto
            System.out.println("Directorio de trabajo actual: " + System.getProperty("user.dir"));
            File file = new File("application.properties");
            System.out.println("¿Existe el archivo en la ruta actual?: " + file.exists());
            System.out.println("Ruta absoluta del archivo buscado: " + file.getAbsolutePath());

            String kafkaTopicRequest = System.getenv("KAFKA_TOPIC_REQUEST");
            String kafkaTopicRequestPlanilla = System.getenv("KAFKA_TOPIC_REQUEST_PLANILLA");
            String port = System.getenv("PORT");

            // Mostrar los valores para depuración
            System.out.println("📋 Configuración cargada desde variables de entorno:");
            System.out.println("   🔹 kafka_topic_request = " + kafkaTopicRequest);
            System.out.println("   🔹 kafka_topic_request_planilla = " + kafkaTopicRequestPlanilla);
            System.out.println("   🔹 port = " + port);
            
            properties.load(new FileInputStream("application.properties"));
        } catch (IOException e) {
            System.err.println("No se pudo cargar application.properties: " + e.getMessage());
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