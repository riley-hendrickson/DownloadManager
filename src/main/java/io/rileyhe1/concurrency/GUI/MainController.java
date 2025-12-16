package io.rileyhe1.concurrency.GUI;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;

import io.rileyhe1.concurrency.DownloadManager;
import io.rileyhe1.concurrency.Data.DownloadConfig;
import io.rileyhe1.concurrency.Data.DownloadException;
import io.rileyhe1.concurrency.Util.Download;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
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

    @FXML private Button minimizeButton;
    @FXML private Button maximizeButton;
    @FXML private Button closeButton;

    @FXML private TableView<DownloadRow> downloadsTable;
    @FXML private TableColumn<DownloadRow, String> destinationColumn;
    @FXML private TableColumn<DownloadRow, String> urlColumn;
    @FXML private TableColumn<DownloadRow, String> statusColumn;
    @FXML private TableColumn<DownloadRow, DownloadRow> progressColumn;
    @FXML private TableColumn<DownloadRow, DownloadRow> actionsColumn;
    
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
            // Setup table columns
            setupTableColumns();

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

            // load any persisted downloads
            loadPersistedDownloads();

            // start ui update timer
            startUpdateTimer();
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
        // set destination column
        destinationColumn.setCellValueFactory(new PropertyValueFactory<>("fileName"));
        destinationColumn.setPrefWidth(200);

        // set url column
        urlColumn.setCellValueFactory(new PropertyValueFactory<>("url"));
        urlColumn.setPrefWidth(300);

        // set status column
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusColumn.setPrefWidth(100);
        statusColumn.setCellFactory(column -> new TableCell<DownloadRow, String>()
        {
            @Override
            protected void updateItem(String status, boolean empty)
            {
                super.updateItem(status, empty);
                
                if(empty || status == null)
                {
                    setText(null);
                    setStyle("");
                }
                else
                {
                    setText(status);

                    // color code based on status
                    switch(status)
                    {
                        case "DOWNLOADING":
                            setStyle("-fx-text-fill: #28A745; -fx-font-weight: bold;");
                            break;
                        case "FAILED":
                        case "CANCELLED":
                            setStyle("-fx-text-fill: #E74C3C; -fx-font-weight: bold;");
                            break;
                        case "PAUSED":
                            setStyle("-fx-text-fill: #007BFF; -fx-font-weight: bold;");
                            break;
                        case "COMPLETED":
                            setStyle("-fx-text-fill: #6C757D; -fx-font-weight: bold;");
                            break;
                    }
                }
            }
        });

        // set progress column
        progressColumn.setCellValueFactory(cellData -> 
            new SimpleObjectProperty<>(cellData.getValue()));
        progressColumn.setPrefWidth(200);
        progressColumn.setCellFactory(column -> new TableCell<DownloadRow, DownloadRow>()
        {
            private final ProgressBar progressBar = new ProgressBar();
            private final Label progressLabel = new Label();
            private final VBox container = new VBox(5);
            
            {
                progressBar.setPrefWidth(150);
                progressBar.setPrefHeight(20);
                progressLabel.setStyle("-fx-font-size: 11; -fx-text-fill: #495057;");
                container.setAlignment(Pos.CENTER);
                container.getChildren().addAll(progressBar, progressLabel);
            }
            
            @Override
            protected void updateItem(DownloadRow row, boolean empty)
            {
                super.updateItem(row, empty);
                
                if(empty || row == null)
                {
                    setGraphic(null);
                }
                else
                {
                    double progress = row.getProgress() / 100.0;
                    progressBar.setProgress(progress);
                    
                    // Format progress text
                    String progressText = String.format("%.1f%% (%s / %s)", 
                        row.getProgress(),
                        formatBytes(row.getDownloadedBytes()),
                        formatBytes(row.getTotalBytes()));
                    
                    progressLabel.setText(progressText);
                    setGraphic(container);
                }
            }
        });
        
        // Actions column - shows pause/resume/cancel buttons
        actionsColumn.setCellValueFactory(cellData -> 
            new SimpleObjectProperty<>(cellData.getValue()));
        actionsColumn.setPrefWidth(150);
        actionsColumn.setCellFactory(column -> new TableCell<DownloadRow, DownloadRow>()
        {
            private final Button pauseResumeButton = new Button();
            private final Button cancelButton = new Button("✖");
            private final HBox container = new HBox(5);
            
            {
                pauseResumeButton.getStyleClass().add("toolbar-button-info");
                cancelButton.getStyleClass().add("toolbar-button-cancel");
                container.setAlignment(Pos.CENTER);
                container.getChildren().addAll(pauseResumeButton, cancelButton);
            }
            
            @Override
            protected void updateItem(DownloadRow row, boolean empty)
            {
                super.updateItem(row, empty);
                
                if(empty || row == null)
                {
                    setGraphic(null);
                }
                else
                {
                    // Configure pause/resume button based on state
                    String status = row.getStatus();
                    
                    if("DOWNLOADING".equals(status))
                    {
                        pauseResumeButton.setText("⏸");
                        pauseResumeButton.setOnAction(e -> handlePause(row));
                        pauseResumeButton.setDisable(false);
                    }
                    else if("PAUSED".equals(status) || "PENDING".equals(status))
                    {
                        pauseResumeButton.setText("▶");
                        pauseResumeButton.setOnAction(e -> handleResume(row));
                        pauseResumeButton.setDisable(false);
                    }
                    else
                    {
                        pauseResumeButton.setText("—");
                        pauseResumeButton.setDisable(true);
                    }
                    
                    // Configure cancel button
                    if("COMPLETED".equals(status) || "CANCELLED".equals(status))
                    {
                        cancelButton.setDisable(true);
                    }
                    else
                    {
                        cancelButton.setOnAction(e -> handleCancel(row));
                        cancelButton.setDisable(false);
                    }
                    
                    setGraphic(container);
                }
            }
        });
    }

    // Method to load persisted downloads on startup //

    private void loadPersistedDownloads()
    {
        try
        {
            List<Download> downloads = downloadManager.getAllDownloads();
            for(Download download : downloads)
            {
                DownloadRow row = new DownloadRow(download);
                downloadRows.add(row);
            }

            if(!downloads.isEmpty())
            {
                statusLabel.setText("Loaded " + downloads.size() + " saved download(s)");
            }
        }
        catch(Exception e)
        {
            showError("Load Error", "Failed to load persisted downloads: " + e.getMessage());
        }
    }

    private void startUpdateTimer()
    {
        updateTimer = new Timer(true);

        updateTimer.scheduleAtFixedRate(new TimerTask() 
        {
            @Override
            public void run()
            {
                Platform.runLater(() -> 
                {
                    for(DownloadRow row : downloadRows)
                    {
                        row.refresh();
                    }
                    downloadsTable.refresh();
                    updateStatusBar();
                });
            }
        }, 0, 100);
    }

    private void updateStatusBar()
    {
        int totalDownloads = downloadRows.size();
        int activeDownloads = 0;
        long totalSpeed = 0;

        for(DownloadRow row : downloadRows)
        {
            String status = row.getStatus();
            if(status.equalsIgnoreCase("DOWNLOADING")) activeDownloads++;
        }

        activeDownloadsLabel.setText("Active Downloads: " + activeDownloads);
        // placeholder for actually calculating speed as a later feature
        totalSpeedLabel.setText("Speed: " + totalSpeed + " kb/s");

        if(activeDownloads > 0)
        {
            statusLabel.setText("Downloading " + activeDownloads + " file(s)...");
        }
        else if(totalDownloads > 0)
        {
            statusLabel.setText("Ready");
        }
        else
        {
            statusLabel.setText("Idle");
        }
    }

    // Toolbar button method handlers (add download, pause all, resume all, cancel all, open settings) // 

    @FXML
    private void handleAddDownload(MouseEvent event)
    {
        TextInputDialog urlDialog = new TextInputDialog();
        urlDialog.setTitle("Start a New Download");
        urlDialog.setHeaderText("Enter download URL");
        urlDialog.setContentText("URL:");

        Optional<String> urlResult = urlDialog.showAndWait();
        if(!urlResult.isPresent() || urlResult.get().trim().isEmpty())
        {
            return;
        }

        String url = urlResult.get().trim();
        System.out.println(url);
    }

    @FXML
    private void handleResumeAll(MouseEvent event)
    {
        // TODO: implement
    }

    @FXML
    private void handlePauseAll(MouseEvent event)
    {
        // TODO: implement
    }

    @FXML
    private void handleCancelAll(MouseEvent event)
    {
        // TODO: implement
    }

    @FXML
    private void handleOpenSettings(MouseEvent event)
    {
        // TODO: implement
    }

    // Methods for Pause, Resume, Cancel of selected download //
    @FXML
    private void handlePause(DownloadRow row)
    {
        // TODO: implement
    }

    @FXML
    private void handleResume(DownloadRow row)
    {
        // TODO: implement
    }

    @FXML
    private void handleCancel(DownloadRow row)
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
        if(updateTimer != null) updateTimer.cancel();
        if(downloadManager != null) downloadManager.shutdown();
        Platform.exit();
    }

    private void showError(String title, String message)
    {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private String formatBytes(long bytes)
    {
        if(bytes < 1024) return bytes + " B";
        if(bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if(bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    // inner classes //
    public static class DownloadRow
    {
        private final Download download;
        private String fileName;
        private String url;
        private String status;
        private double progress;
        private long downloadedBytes;
        private long totalBytes;

        public DownloadRow(Download download)
        {
            this.download = download;
            this.fileName = download.getFileName();
            this.url = download.getUrl();
            this.status = download.getState().toString();
            this.progress = download.getProgress();
            this.downloadedBytes = download.getDownloadedBytes();
            this.totalBytes = download.getTotalSize();
        }

        public void refresh()
        {
            this.status = download.getState().toString();
            this.progress = download.getProgress();
            this.downloadedBytes = download.getDownloadedBytes();
        }
        
        public Download getDownload()
        {
            return download;
        }
        public String getFileName()
        {
            return fileName;
        }

        public String getUrl()
        {
            return url;
        }

        public String getStatus()
        {
            return status;
        }

        public double getProgress()
        {
            return progress;
        }

        public long getDownloadedBytes()
        {
            return downloadedBytes;
        }

        public long getTotalBytes()
        {
            return totalBytes;
        }
    }
}
