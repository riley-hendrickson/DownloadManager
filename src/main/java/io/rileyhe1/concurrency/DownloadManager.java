package io.rileyhe1.concurrency;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import io.rileyhe1.concurrency.Data.DownloadConfig;
import io.rileyhe1.concurrency.Data.DownloadException;
import io.rileyhe1.concurrency.Data.DownloadSnapshot;
import io.rileyhe1.concurrency.Data.DownloadState;
import io.rileyhe1.concurrency.Util.Download;
import io.rileyhe1.concurrency.Util.ProgressTracker;

public class DownloadManager
{
    private static final String DOWNLOADS_FILE = "downloads.json";

    ExecutorService executorService;
    Map<String, Download> activeDownloads;
    DownloadConfig config;

    public DownloadManager(DownloadConfig config) throws IOException, DownloadException
    {
        if (config == null)
        {
            throw new IllegalArgumentException("Config cannot be null");
        }
        this.executorService = Executors.newFixedThreadPool(config.getNumberOfThreads());
        this.activeDownloads = new ConcurrentHashMap<>();
        this.config = config;

        loadDownloads();
    }

    public synchronized Download startDownload(String url, String destination) throws DownloadException
    {
        // input validation
        if(url == null || url.trim().isEmpty()) throw new IllegalArgumentException("url cannot be empty/null");
        if(destination == null || destination.trim().isEmpty()) throw new IllegalArgumentException("destination cannot be empty/null");
        // check for existing downloads already using the given destination
        for(Download download : activeDownloads.values())
        {
            if(download.getDestination().equals(destination))
            {
                throw new IllegalArgumentException("Invalid destination, there is an active download using the given destination");
            }
        }
        // create the download and store it, more input validation is done in the Download constructor
        ProgressTracker progressTracker = new ProgressTracker();
        Download download = new Download(url, destination, config, progressTracker, executorService);
        activeDownloads.put(download.getId(), download);

        // start the download and return its handle
        download.start();
        return download;
    }

    public void pauseDownload(String downloadId)
    {
        // input validation
        if(downloadId == null || downloadId.trim().isEmpty()) throw new IllegalArgumentException("download id cannot be null/empty");
        Download download = activeDownloads.get(downloadId);
        if(download == null) throw new IllegalArgumentException("Invalid download id, download is not present in active downloads map");
        download.pause();
    }

    public void resumeDownload(String downloadId)
    {
        // input validation
        if(downloadId == null || downloadId.trim().isEmpty()) throw new IllegalArgumentException("download id cannot be null/empty");
        Download download = activeDownloads.get(downloadId);
        if(download == null) throw new IllegalArgumentException("Invalid download id, download is not present in active downloads map");
        
        // Check if this is a loaded download that hasn't been started yet
        if(download.getState() == DownloadState.PENDING)
        {
            download.startExisting();
        }
        // Use regular resume for paused downloads
        else if(download.getState() == DownloadState.PAUSED)
        {
            download.resume();
        }
        else
        {
            throw new IllegalStateException("Cannot resume download in state: " + download.getState());
        }
    }

    public void cancelDownload(String downloadId)
    {
        // input validation
        if(downloadId == null || downloadId.trim().isEmpty()) throw new IllegalArgumentException("download id cannot be null/empty");
        Download download = activeDownloads.get(downloadId);
        if(download == null) throw new IllegalArgumentException("Invalid download id, download is not present in active downloads map");
        download.cancel();
        activeDownloads.remove(downloadId);
    }

    private void saveDownloads() throws IOException
    {
        List<DownloadSnapshot> snapshots = new ArrayList<>();
        
        // Collect downloads that should be persisted
        for(Download download : activeDownloads.values())
        {
            DownloadState state = download.getState();
            if(state == DownloadState.DOWNLOADING || 
            state == DownloadState.PAUSED || 
            state == DownloadState.FAILED)
            {
                snapshots.add(download.createSnapshot());
            }
        }
        
        // Convert to JSON with pretty printing
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(snapshots);
        
        // Write to file
        try(FileWriter writer = new FileWriter(DOWNLOADS_FILE))
        {
            writer.write(json);
        }
    }

    public void loadDownloads() throws IOException, DownloadException
    {
        // Check if file exists
        if(!Files.exists(Paths.get(DOWNLOADS_FILE)))
        {
            return; // No saved downloads
        }
        
        // Read and deserialize JSON
        Gson gson = new Gson();
        Type listType = new TypeToken<List<DownloadSnapshot>>(){}.getType();
        
        List<DownloadSnapshot> snapshots;
        try(FileReader reader = new FileReader(DOWNLOADS_FILE))
        {
            snapshots = gson.fromJson(reader, listType);
        }
        
        if(snapshots == null || snapshots.isEmpty())
        {
            return;
        }
        
        // Recreate downloads from snapshots
        for(DownloadSnapshot snapshot : snapshots)
        {
            // Create ProgressTracker with saved progress
            ProgressTracker tracker = new ProgressTracker();
            if(snapshot.getChunkProgress() != null)
            {
                for(Map.Entry<Integer, Long> entry : snapshot.getChunkProgress().entrySet())
                {
                    tracker.updateProgress(entry.getKey(), entry.getValue());
                }
            }
            
            // Create Download (will be in PENDING state initially)
            Download download = new Download(snapshot, config, tracker, executorService);
            
            // Add to active downloads
            activeDownloads.put(download.getId(), download);
        }
    }

    public Download getDownload(String downloadId)
    {
        return activeDownloads.get(downloadId);
    }

    public void shutdown()
    {
        // Pause all active downloads
        for(Download download : activeDownloads.values())
        {
            if(download.getState() == DownloadState.DOWNLOADING)
            {
                try
                {
                    download.pause();
                }
                catch(IllegalStateException e)
                {
                    // Download might have completed/failed between check and pause - which we will ignore
                }
            }
        }
        
        // Save state to disk
        try
        {
            saveDownloads();
        }
        catch(IOException e)
        {
            System.err.println("Failed to save downloads: " + e.getMessage());
            // Continue with shutdown even if save fails
        }
        
        // Cancel all downloads for cleanup
        for(Download download : activeDownloads.values())
        {
            try
            {
                download.cancel();
            }
            catch(IllegalStateException e)
            {
                // Illegal state exception only throws if already completed/cancelled - so we'll just ignore it
            }
        }
        
        // Shutdown executor
        executorService.shutdown();
        
        try
        {
            // Wait for termination with timeout (60 seconds)
            if(!executorService.awaitTermination(60, TimeUnit.SECONDS))
            {
                // Timeout ran out so we'll force shutdown
                executorService.shutdownNow();
                
                // Wait again after forced shutdown
                if(!executorService.awaitTermination(60, TimeUnit.SECONDS))
                {
                    System.err.println("ExecutorService did not terminate");
                }
            }
        }
        catch(InterruptedException e)
        {
            // Current thread was interrupted during shutdown
            Thread.currentThread().interrupt();
            // Force shutdown
            executorService.shutdownNow();
        }
        
        // Lastly we clear the map
        activeDownloads.clear();
    }
    public static void main(String[] args)
    {
        System.out.println("Hello world!");
    }
}