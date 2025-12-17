package io.rileyhe1.concurrency.GUI;

import java.io.File;
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
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
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
            System.out.println(System.getProperty("java.io.tmpdir"));
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
        destinationColumn.setCellValueFactory(cellData -> cellData.getValue().getFileNameProperty());
        destinationColumn.setPrefWidth(200);
        destinationColumn.getStyleClass().add("destination-cell");

        // set url column
        urlColumn.setCellValueFactory(cellData -> cellData.getValue().getUrlProperty());
        urlColumn.setPrefWidth(300);
        urlColumn.getStyleClass().add("url-cell");

        // set status column
        statusColumn.setCellValueFactory(cellData -> cellData.getValue().getStatusProperty());
        statusColumn.setPrefWidth(100);
        statusColumn.getStyleClass().add("status-cell");
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
            private DownloadRow currentRow = null;
            
            {
                progressBar.setPrefWidth(130);
                progressBar.setMinWidth(130);
                progressBar.setMaxWidth(130);
                progressBar.setPrefHeight(16);
                progressBar.setMinHeight(16);

                progressLabel.setStyle("-fx-font-size: 13; -fx-text-fill: #495057;");
                container.setAlignment(Pos.CENTER);
                container.setSpacing(3);
                container.setPrefHeight(50);
                container.getChildren().addAll(progressBar, progressLabel);
            }
            
            @Override
            protected void updateItem(DownloadRow row, boolean empty)
            {
                super.updateItem(row, empty);
                
                if(empty || row == null)
                {
                    if(currentRow != null)
                    {
                        progressBar.progressProperty().unbind();
                    }
                    currentRow = null;
                    setGraphic(null);
                }
                else
                {
                    // only bind progress bar to property if this is a new row
                    if(currentRow != row)
                    {
                        // Unbind from previous row if exists
                        if(currentRow != null)
                        {
                            progressBar.progressProperty().unbind();
                        }
                
                        currentRow = row;

                        // bind progress bar to property
                        progressBar.progressProperty().bind(row.getProgressProperty().divide(100.0));
                    
                        // Add listeners for label updates
                        row.getProgressProperty().addListener((obs, oldVal, newVal) -> 
                        {
                            updateProgressLabel(row);
                        });
                        row.getDownloadedBytesProperty().addListener((obs, oldVal, newVal) -> 
                        {
                            updateProgressLabel(row);
                        });
                    }
                    
                    updateProgressLabel(row);
                    setGraphic(container);
                }
                downloadsTable.setFixedCellSize(60);
            }

            private void updateProgressLabel(DownloadRow row)
            {
                String progressText = String.format("%.1f%% (%s / %s)", 
                    row.getProgress(),
                    formatBytes(row.getDownloadedBytes()),
                    formatBytes(row.getTotalBytes()));
                progressLabel.setText(progressText);
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
                    // Listen to status changes
                    row.getStatusProperty().addListener((obs, oldVal, newVal) -> 
                    {
                        updateButtons(row);
                    });
                    
                    updateButtons(row);
                    setGraphic(container);
                }
            }

            private void updateButtons(DownloadRow row)
            {
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
                
                if("COMPLETED".equals(status) || "CANCELLED".equals(status))
                {
                    cancelButton.setDisable(true);
                }
                else
                {
                    cancelButton.setOnAction(e -> handleCancel(row));
                    cancelButton.setDisable(false);
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

    // Toolbar button method handlers (add download, pause all, resume all, cancel all) // 

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
        String fileExtension = url.substring(url.lastIndexOf("."));
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Download As");

        String suggestedName = url.substring(url.lastIndexOf("/") + 1);
        if(!suggestedName.isEmpty())
        {
            fileChooser.setInitialFileName(suggestedName);
        }

        File file = fileChooser.showSaveDialog(root.getScene().getWindow());
        if(file == null) return;

        String name = file.getName();
        String destination = name.contains(fileExtension) ? file.getAbsolutePath() : file.getAbsolutePath() + fileExtension;

        try
        {
            Download download = downloadManager.startDownload(url, destination);
            DownloadRow row = new DownloadRow(download);
            downloadRows.add(row);

            statusLabel.setText("Started Download: " + file.getName());
        }
        catch(Exception e)
        {
            showError("Download Error", "Failed to start download: " + e.getMessage());
        }
    }

    @FXML
    private void handleResumeAll(MouseEvent event)
    {
        int resumed = 0; 

        for(DownloadRow row : downloadRows)
        {
            try
            {
                String status = row.getStatus();
                if("PAUSED".equals(status) || "PENDING".equals(status))
                {
                    downloadManager.resumeDownload(row.getDownload().getId());
                    resumed++;
                }
            }
            catch(Exception e)
            {
                // ignore and try to continue resuming other downloads
            }
        }

        if(resumed > 0)
        {
            statusLabel.setText("Resumed " + resumed + " download(s)");
        }
    }

    @FXML
    private void handlePauseAll(MouseEvent event)
    {
        int paused = 0;

        for(DownloadRow row : downloadRows)
        {
            try
            {
                if("DOWNLOADING".equals(row.getStatus()))
                {
                    downloadManager.pauseDownload(row.getDownload().getId());
                    paused++;
                }
            }
            catch(Exception e)
            {
                // ignore and continue to attempt pausing other downloads
            }
        }

        if(paused > 0)
        {
            statusLabel.setText("Paused " + paused + " download(s)");
        }
    }

    @FXML
    private void handleCancelAll(MouseEvent event)
    {
        Alert alert = new Alert(AlertType.CONFIRMATION);
        alert.setTitle("Confirm Cancel All");
        alert.setHeaderText("Cancel all downloads?");
        alert.setContentText("This will cancel all active and paused downloads. This action cannot be undone.");
        
        Optional<ButtonType> result = alert.showAndWait();
        if(result.isPresent() && result.get() == ButtonType.OK)
        {
            int cancelled = 0;

            for(DownloadRow row : downloadRows)
            {
                try
                {
                    String status = row.getStatus();
                    if(!status.equals("CANCELLED") && !status.equals("COMPLETED"))
                    {
                        downloadManager.cancelDownload(row.getDownload().getId());
                        cancelled++;   
                    }
                }
                catch(Exception e)
                {
                    // ignore and continue trying to cancel other downloads
                }
            }

            if(cancelled > 0)
            {
                statusLabel.setText("Cancelled " + cancelled + " download(s)");
            }
        }
    }   

    // Methods for Pause, Resume, Cancel of selected download //
    @FXML
    private void handlePause(DownloadRow row)
    {
        try
        {
            downloadManager.pauseDownload(row.getDownload().getId());
            statusLabel.setText("Paused " + row.getFileName());
        }
        catch(Exception e)
        {
            showError("Pause Error", "Failed to pause download for  " + row.getFileName() + " " + e.getMessage());
        }
    }

    @FXML
    private void handleResume(DownloadRow row)
    {
        try
        {
            downloadManager.resumeDownload(row.getDownload().getId());
            statusLabel.setText("Resumed " + row.getFileName());
        }
        catch(Exception e)
        {
            showError("Resume Error", "Failed to resume download for  " + row.getFileName() + " " + e.getMessage());
        }
    }

    @FXML
    private void handleCancel(DownloadRow row)
    {
        try
        {
            downloadManager.cancelDownload(row.getDownload().getId());
            statusLabel.setText("Cancelled " + row.getFileName());
        }
        catch(Exception e)
        {
            showError("Cancellation Error", "Failed to cancel download for  " + row.getFileName() + " " + e.getMessage());
        }
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
        if(resizing) return;
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
        private final StringProperty fileName;
        private final StringProperty url;
        private final StringProperty status;
        private final DoubleProperty progress;
        private final LongProperty downloadedBytes;
        private final LongProperty totalBytes;

        public DownloadRow(Download download)
        {
            this.download = download;
            this.fileName = new SimpleStringProperty(download.getDestination());
            this.url = new SimpleStringProperty(download.getUrl());
            this.status = new SimpleStringProperty(download.getState().toString());
            this.progress = new SimpleDoubleProperty(download.getProgress());
            this.downloadedBytes = new SimpleLongProperty(download.getDownloadedBytes());
            this.totalBytes = new SimpleLongProperty(download.getTotalSize());
        }

        public void refresh()
        {
            status.set(download.getState().toString());
            progress.set(download.getProgress());
            downloadedBytes.set(download.getDownloadedBytes());
        }
        
        public Download getDownload()
        {
            return download;
        }

        public StringProperty getFileNameProperty()
        {
            return fileName;
        }
        public String getFileName()
        {
            return fileName.get();
        }
        public StringProperty getUrlProperty()
        {
            return url;
        }
        public String getUrl()
        {
            return url.get();
        }

        public StringProperty getStatusProperty()
        {
            return status;
        }
        public String getStatus()
        {
            return status.get();
        }

        public DoubleProperty getProgressProperty()
        {
            return progress;
        }
        public double getProgress()
        {
            return progress.get();
        }

        public LongProperty getDownloadedBytesProperty()
        {
            return downloadedBytes;
        }
        public long getDownloadedBytes()
        {
            return downloadedBytes.get();
        }

        public LongProperty getTotalBytesProperty()
        {
            return totalBytes;
        }
        public long getTotalBytes()
        {
            return totalBytes.get();
        }
    }
}
