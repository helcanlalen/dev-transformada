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


            // Intenta cargar desde la ubicación específica
            File deploymentFile = new File("/deployments/application.properties");
            if (deploymentFile.exists()) {
                System.out.println("✅ El archivo existe en /deployments/application.properties");
                FileInputStream fis = new FileInputStream(deploymentFile);
                properties.load(fis);
                
                // Mostrar todo el contenido del archivo
                System.out.println("📄 Contenido del archivo application.properties:");
                for (String key : properties.stringPropertyNames()) {
                    System.out.println("   🔑 " + key + " = " + properties.getProperty(key));
                }
                fis.close();
            } else {
                System.out.println("❌ El archivo NO existe en /deployments/application.properties");
                // Intenta cargar desde la ubicación actual
                if (file.exists()) {
                    properties.load(new FileInputStream(file));
                } else {
                    System.out.println("❌ El archivo NO existe en ninguna ubicación conocida");
                }
            }

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