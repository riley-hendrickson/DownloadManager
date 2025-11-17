package io.rileyhe1.concurrency;

import java.util.Map;
import java.util.concurrent.ExecutorService;

import io.rileyhe1.concurrency.Data.Download;
import io.rileyhe1.concurrency.Data.DownloadConfig;

public class DownloadManager
{
    ExecutorService executorService;
    Map<String, Download> activeDownloads;
    DownloadConfig config;

    public Download startDownload(String url, String destination)
    {
        return null;
    }

    public void pauseDownload(String downloadId)
    {
        
    }

    public void resumeDownload(String downloadId)
    {

    }

    public void cancelDownload(String downloadId)
    {

    }

    public Download getDownload(String downloadId)
    {
        return null;
    }

    public void shutdown()
    {

    }
    public static void main(String[] args)
    {
        System.out.println("Hello world!");
    }
}