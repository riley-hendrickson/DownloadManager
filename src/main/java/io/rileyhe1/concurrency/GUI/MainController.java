package io.rileyhe1.concurrency.GUI;

import javafx.fxml.FXML;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.scene.input.MouseEvent;
import javafx.scene.Cursor;
import javafx.scene.Node;

public class MainController
{
    @FXML
    private BorderPane root;
    @FXML
    private HBox titleBar;
    @FXML
    private Button minimizeButton;
    @FXML
    private Button maximizeButton;
    @FXML
    private Button closeButton;
    @FXML
    private TextField searchBox;
    // fields for resizing, moving the window, and snapping the window to the top of the screen to restore full screen
    private static final int RESIZE_MARGIN = 5;

    private boolean resizing = false;
    private boolean resizeRight;
    private boolean resizeLeft;
    private boolean resizeBottom;
    private boolean resizeTop;

    private double startX;
    private double startY;
    private double startScreenX;
    private double startScreenY;
    private double startWidth;
    private double startHeight;

    // For window dragging
    private double xOffset = 0;
    private double yOffset = 0;

    private final Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
    private boolean isMaximized = true;

    @FXML
    private void initialize()
    {
        setupResizeHandlers();
    }

    // ----- Button handlers -----

    private void setupResizeHandlers()
    {
        root.setOnMouseMoved(this::handleMouseMovedForResize);
        root.setOnMousePressed(this::handleMousePressedForResize);
        root.setOnMouseDragged(this::handleMouseDraggedForResize);
    }

    private void handleMouseMovedForResize(MouseEvent event)
    {
        Stage stage = (Stage) root.getScene().getWindow();
        double mouseX = event.getX();
        double mouseY = event.getY();
        double width = stage.getWidth();
        double height = stage.getHeight();

        resizeLeft = mouseX < RESIZE_MARGIN;
        resizeRight = mouseX > width - RESIZE_MARGIN;
        resizeTop = mouseY < RESIZE_MARGIN;
        resizeBottom = mouseY > height - RESIZE_MARGIN;

        Cursor cursor = Cursor.DEFAULT;

        if (resizeRight && resizeBottom)
        {
            cursor = Cursor.SE_RESIZE;
        } else if (resizeRight && resizeTop)
        {
            cursor = Cursor.NE_RESIZE;
        } else if (resizeLeft && resizeBottom)
        {
            cursor = Cursor.SW_RESIZE;
        } else if (resizeLeft && resizeTop)
        {
            cursor = Cursor.NW_RESIZE;
        } else if (resizeRight)
        {
            cursor = Cursor.E_RESIZE;
        } else if (resizeLeft)
        {
            cursor = Cursor.W_RESIZE;
        } else if (resizeBottom)
        {
            cursor = Cursor.S_RESIZE;
        } else if (resizeTop)
        {
            cursor = Cursor.N_RESIZE;
        }

        root.setCursor(cursor);
    }

    private void handleMousePressedForResize(MouseEvent event)
    {
        Stage stage = (Stage) root.getScene().getWindow();

        startX = stage.getX();
        startY = stage.getY();
        startWidth = stage.getWidth();
        startHeight = stage.getHeight();
        startScreenX = event.getScreenX();
        startScreenY = event.getScreenY();

        resizing = root.getCursor() != Cursor.DEFAULT;
    }

    private void handleMouseDraggedForResize(MouseEvent event)
    {
        if (!resizing)
        {
            return;
        }

        Stage stage = (Stage) root.getScene().getWindow();

        double deltaX = event.getScreenX() - startScreenX;
        double deltaY = event.getScreenY() - startScreenY;

        if (resizeRight)
        {
            stage.setWidth(Math.max(400, startWidth + deltaX));
        }
        if (resizeBottom)
        {
            stage.setHeight(Math.max(300, startHeight + deltaY));
        }
        if (resizeLeft)
        {
            double newWidth = Math.max(400, startWidth - deltaX);
            double newX = startX + (startWidth - newWidth);
            stage.setWidth(newWidth);
            stage.setX(newX);
        }
        if (resizeTop)
        {
            double newHeight = Math.max(300, startHeight - deltaY);
            double newY = startY + (startHeight - newHeight);
            stage.setHeight(newHeight);
            stage.setY(newY);
        }
    }

    @FXML
    private void handleMinimize()
    {
        Stage stage = (Stage) minimizeButton.getScene().getWindow();
        stage.setIconified(true);
    }

    @FXML
    private void handleMaximize()
    {
        Stage stage = (Stage) root.getScene().getWindow();
        if(!isMaximized)
        {
            isMaximized = true;

            stage.setX(bounds.getMinX());
            stage.setY(bounds.getMinY());
            stage.setWidth(bounds.getWidth());
            stage.setHeight(bounds.getHeight());
        }
        else
        {
            isMaximized = false;

            double targetWidth  = bounds.getWidth()  * 0.75;    // 75% of screen width
            double targetHeight = bounds.getHeight() * 0.75;    // 75% of screen height

            // center that rectangle inside the visual bounds
            double targetX = bounds.getMinX() + (bounds.getWidth()  - targetWidth)  / 2;
            double targetY = bounds.getMinY() + (bounds.getHeight() - targetHeight) / 2;

            stage.setX(targetX);
            stage.setY(targetY);
            stage.setWidth(targetWidth);
            stage.setHeight(targetHeight);
        }
    }

    @FXML
    private void handleClose()
    {
        Stage stage = (Stage) closeButton.getScene().getWindow();
        stage.close();
    }

    // ----- Dragging handlers -----

    @FXML
    private void handleTitleBarPressed(MouseEvent event)
    {
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        xOffset = event.getScreenX() - stage.getX();
        yOffset = event.getScreenY() - stage.getY();
    }

    @FXML
    private void handleTitleBarDragged(MouseEvent event)
    {
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        // only move the window if the user is attempting to move it within the visual bounds
        if(event.getScreenX() - xOffset > bounds.getMinX() && event.getScreenX() - xOffset < bounds.getMaxX()) stage.setX(event.getScreenX() - xOffset);
        if(event.getScreenY() - yOffset > bounds.getMinY() && event.getScreenY() - yOffset < bounds.getMaxY()) stage.setY(event.getScreenY() - yOffset);
        // resize the window if the window is maximized and the user is trying to move the window
        if(isMaximized && event.getScreenY() - yOffset < bounds.getMaxY() || event.getScreenX() - xOffset < bounds.getMaxX())
        {
            isMaximized = false;
            double targetWidth  = bounds.getWidth()  * 0.75;    // 75% of screen width
            double targetHeight = bounds.getHeight() * 0.75;    // 75% of screen height
            stage.setWidth(targetWidth);
            stage.setHeight(targetHeight);
        }
        // maximize when the user drags the window to the top of the screen
        if(!isMaximized && event.getScreenY() - yOffset < 3) handleMaximize();
    }
}
