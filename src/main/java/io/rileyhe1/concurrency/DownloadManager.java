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

    Map<String, Download> activeDownloads;
    DownloadConfig config;

    public DownloadManager(DownloadConfig config) throws IOException, DownloadException
    {
        if (config == null)
        {
            throw new IllegalArgumentException("Config cannot be null");
        }
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
        Download download = new Download(url, destination, config, progressTracker);
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
            if(state == DownloadState.STOPPED)
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
            Download download = new Download(snapshot, config, tracker);
            
            // Add to active downloads
            activeDownloads.put(download.getId(), download);
        }
    }

    public Download getDownload(String downloadId)
    {
        if(downloadId == null) return null;
        return activeDownloads.get(downloadId);
    }

    public List<Download> getAllDownloads()
    {
        return new ArrayList<>(activeDownloads.values());
    }

    public void shutdown()
    {
        // Stop all active and paused downloads
        for(Download download : activeDownloads.values())
        {
            DownloadState downloadState = download.getState();
            if(downloadState == DownloadState.DOWNLOADING || 
               downloadState == DownloadState.PAUSED)
            {
                try
                {
                    // if the download is running, try to pause it to allow any current read/write to finish before stopping it to prevent data loss
                    if(downloadState != DownloadState.PAUSED)
                    {
                        download.pause();
                        Thread.sleep(10);
                    }
                    download.stop();
                }
                catch(IllegalStateException e)
                {
                    // Download might have completed/failed between check and stop - which we will ignore
                }
                catch(InterruptedException e)
                {
                    // Manager thread interrupted while waiting for download to pause, we'll just restore interrupted state
                    Thread.currentThread().interrupt();
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
            // System.err.println("Failed to save downloads: " + e.getMessage());
            // Continue with shutdown even if save fails
        }
        
        // Cancel all leftover downloads for cleanup (Completed or cancelled downloads)
        for(Download download : activeDownloads.values())
        {
            DownloadState state = download.getState();
            if(state == DownloadState.COMPLETED ||
               state == DownloadState.CANCELLED)
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
        }
        
        // Lastly we clear the map
        activeDownloads.clear();
    }
    // convinience method for GUI
    // private void validateURL(String url) throws IllegalArgumentException
    // {
    //     try 
    //     {
    //         URI.create(url).toURL();
    //     }
    //     catch(Exception e)
    //     {
    //         throw new IllegalArgumentException("Invalid URL format: " + url, e);
    //     }
    // }
}