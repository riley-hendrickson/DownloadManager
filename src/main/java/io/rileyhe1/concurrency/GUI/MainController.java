package io.rileyhe1.concurrency.GUI;

import java.io.IOException;
import java.util.Timer;

import io.rileyhe1.concurrency.DownloadManager;
import io.rileyhe1.concurrency.Data.DownloadConfig;
import io.rileyhe1.concurrency.Data.DownloadException;
import io.rileyhe1.concurrency.Util.Download;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.scene.input.MouseEvent;
import javafx.scene.Cursor;

public class MainController
{
    private Stage stage; 
    private DownloadManager downloadManager;
    private ObservableList<DownloadRow> downloadRows;
    private Timer updateTimer;

    @FXML private BorderPane root;
    @FXML private HBox titleBar;

    @FXML private Button minimizeButton;
    @FXML private Button maximizeButton;
    @FXML private Button closeButton;

    @FXML private TableView<DownloadRow> downloadsTable;
    @FXML private TableColumn<DownloadRow, String> destinationColumn;
    @FXML private TableColumn<DownloadRow, String> urlColumn;
    @FXML private TableColumn<DownloadRow, String> statusColumn;
    @FXML private TableColumn<DownloadRow, Double> progressColumn;
    @FXML private TableColumn<DownloadRow, Void> actionsColumn;
    
    @FXML private TextField searchBox;
    @FXML private Label statusLabel;
    @FXML private Label activeDownloadsLabel;
    @FXML private Label totalSpeedLabel;


    // fields for resizing, moving the window, and snapping the window to the top of the screen to restore full screen // 
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

    // For window dragging // 
    private double xOffset = 0;
    private double yOffset = 0;

    private final Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
    private boolean isMaximized = true;

    @FXML
    private void initialize()
    {
        try
        {
            // initialize download manager, later add a prompt here asking if the user would like to edit default config settings before any downloads begin
            DownloadConfig config = DownloadConfig.builder()
                .chunkSizeMB(5)
                .timeoutsInSeconds(30)
                .maxRetries(3)
                .retryDelayMS(2000)
                .bufferSize(8192)
                .minSizeForChunking(1024 * 1024)
                .build();
            
            downloadManager = new DownloadManager(config);

            // initialize table data
            downloadRows = FXCollections.observableArrayList();
            downloadsTable.setItems(downloadRows);

            // Setup table columns
            setupTableColumns();

            // load any persisted downloads
            loadPersistedDownloads();

            // start ui update timer
            startUpdateTimer();

            updateStatusBar();
        }
        catch(IOException | DownloadException e)
        {
            showError("Initialization Error", "Failed to initialize download manager");
        }
    }

    public void setStage(Stage stage)
    {
        this.stage = stage;
    }

    private void setupTableColumns()
    {
        // TODO: implement
    }

    // Method to load persisted downloads on startup //

    private void loadPersistedDownloads()
    {
        // TODO: implement
    }

    private void startUpdateTimer()
    {
        // TODO: implement
    }

    private void updateStatusBar()
    {
        // TODO: implement
    }

    // Methods for handling moving and resizing of the window //

    @FXML private void handleMouseMovedForResize(MouseEvent event)
    {
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

    @FXML private void handleMousePressedForResize(MouseEvent event)
    {
        startX = stage.getX();
        startY = stage.getY();
        startWidth = stage.getWidth();
        startHeight = stage.getHeight();
        startScreenX = event.getScreenX();
        startScreenY = event.getScreenY();

        resizing = root.getCursor() != Cursor.DEFAULT;
    }

    @FXML private void handleMouseDraggedForResize(MouseEvent event)
    {
        if (!resizing)
        {
            return;
        }

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


    // Methods for handling moving the window, and snapping in and out of fullscreen depending on window position // 

    @FXML
    private void handleTitleBarPressed(MouseEvent event)
    {
        xOffset = event.getScreenX() - stage.getX();
        yOffset = event.getScreenY() - stage.getY();
    }

    @FXML
    private void handleTitleBarDragged(MouseEvent event)
    {
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

    // Toolbar button method handlers (add download, pause all, resume all, cancel all, open settings) // 

    @FXML
    private void handleAddDownload(ActionEvent event)
    {
        // TODO: implement
    }

    @FXML
    private void handleResumeAll(ActionEvent event)
    {
        // TODO: implement
    }

    @FXML
    private void handlePauseAll(ActionEvent event)
    {
        // TODO: implement
    }

    @FXML
    private void handleCancelAll(ActionEvent event)
    {
        // TODO: implement
    }

    @FXML
    private void handleOpenSettings(ActionEvent event)
    {
        // TODO: implement
    }

    // Methods for Pause, Resume, Cancel of selected download //
    @FXML
    private void handlePause(MouseEvent event)
    {
        // TODO: implement
    }

    @FXML
    private void handleResume(MouseEvent event)
    {
        // TODO: implement
    }

    @FXML
    private void handleCancel(MouseEvent event)
    {
        // TODO: implement
    }


    // Methods for handling title bar's minimize, maximize, and close buttons // 

    @FXML
    private void handleMinimize()
    {
        stage.setIconified(true);
    }

    @FXML
    private void handleMaximize()
    {
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
        if(downloadManager != null) downloadManager.shutdown();
        if(updateTimer != null) updateTimer.cancel();
        Platform.exit();
        // stage.close();
    }

    private void showError(String title, String message)
    {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // inner classes //
    public static class DownloadRow
    {
        private final Download download;
        
        public DownloadRow(Download download)
        {
            this.download = download;
        }
        
        public Download getDownload()
        {
            return download;
        }
    }
    
    private static class DownloadInfo
    {
        final String url;
        final String destination;
        
        DownloadInfo(String url, String destination)
        {
            this.url = url;
            this.destination = destination;
        }
    }
}
