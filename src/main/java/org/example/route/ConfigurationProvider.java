package org.example.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

@ApplicationScoped
public class ConfigurationProvider {
    
    private final Properties properties;
    
    public CustomConfigProvider() {
        properties = new Properties();
        try {
            // Lee el archivo de la raíz del proyecto
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