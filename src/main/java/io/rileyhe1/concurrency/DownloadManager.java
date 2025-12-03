package io.rileyhe1.concurrency;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.rileyhe1.concurrency.Data.DownloadConfig;
import io.rileyhe1.concurrency.Data.DownloadException;
import io.rileyhe1.concurrency.Util.Download;
import io.rileyhe1.concurrency.Util.ProgressTracker;

public class DownloadManager
{
    ExecutorService executorService;
    Map<String, Download> activeDownloads;
    DownloadConfig config;

    public DownloadManager(DownloadConfig config)
    {
        if (config == null)
        {
            throw new IllegalArgumentException("Config cannot be null");
        }
        this.executorService = Executors.newFixedThreadPool(config.getNumberOfThreads());
        this.activeDownloads = new ConcurrentHashMap<>();
        this.config = config;
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
        download.resume();
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

    public Download getDownload(String downloadId)
    {
        return activeDownloads.get(downloadId);
    }

    public void shutdown()
    {

    }
    public static void main(String[] args)
    {
        System.out.println("Hello world!");
    }
}