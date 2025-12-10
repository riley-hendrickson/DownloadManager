package io.rileyhe1.concurrency.GUI;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class DownloadManagerApp extends Application
{

    @Override
    public void start(Stage stage) throws Exception
    {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("main-view.fxml"));

        Parent root = loader.load();
        Scene scene = new Scene(root);

        stage.setTitle("Riley's Concurrent Download Manager");
        stage.setScene(scene);
        // remove the OS title bar so we can create our own better looking one
        stage.initStyle(StageStyle.UNDECORATED);
        // set the window to fill the visual bounds, not using fullscreen so that we don't block the taskbar
        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        stage.setX(bounds.getMinX());
        stage.setY(bounds.getMinY());
        stage.setWidth(bounds.getWidth());
        stage.setHeight(bounds.getHeight());
        
        stage.show();
    }
}