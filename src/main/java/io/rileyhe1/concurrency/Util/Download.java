package io.rileyhe1.concurrency.Util;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

import io.rileyhe1.concurrency.Data.DownloadState;
@SuppressWarnings("unused")
public class Download
{
    private String id;
    private String url;
    private String destination;
    private long totalSize;
    private AtomicLong downloadedBytes = new AtomicLong(0);
    private DownloadState state;
    private List<ChunkDownloader> chunks;
    private CountDownLatch completionLatch;

    public void start()
    {

    }

    public void pause()
    {

    }

    public void resume()
    {

    }

    public void cancel()
    {

    }

    public double getProgress()
    {
        return 0.0;
    }

    public DownloadState getState()
    {
        return null;
    }

    public void awaitCompletion()
    {
        
    }
}
