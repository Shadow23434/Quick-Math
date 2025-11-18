package com.mathspeed.util;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import java.io.File;
import java.io.IOException;
import java.net.URL;

public class ResourceLoader {
    public static Parent loadFXML(String fxmlPath, Class<?> contextClass) throws IOException {
        File file = new File(fxmlPath);
        FXMLLoader loader;
        if (file.exists()) {
            loader = new FXMLLoader(file.toURI().toURL());
        } else {
            // Try to load from classpath
            // First try with full path structure
            String fileName = new File(fxmlPath).getName();
            URL resource = null;

            // Try: /fxml/pages/filename.fxml
            resource = contextClass.getResource("/fxml/pages/" + fileName);

            // Try: /fxml/filename.fxml
            if (resource == null) {
                resource = contextClass.getResource("/fxml/" + fileName);
            }

            if (resource == null) {
                throw new IOException("Cannot find FXML resource: " + fxmlPath + " (tried /fxml/pages/ and /fxml/)");
            }

            loader = new FXMLLoader(resource);
        }
        return loader.load();
    }

    public static String loadCSS(String cssPath, Class<?> contextClass) {
        File file = new File(cssPath);
        if (file.exists()) {
            return file.toURI().toString();
        } else {
            URL resource = contextClass.getResource("/css/" + new File(cssPath).getName());
            return resource != null ? resource.toExternalForm() : null;
        }
    }
}

