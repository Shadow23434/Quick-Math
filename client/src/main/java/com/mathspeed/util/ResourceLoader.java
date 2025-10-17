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
            URL resource = contextClass.getResource("/fxml/" + new File(fxmlPath).getName());
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

