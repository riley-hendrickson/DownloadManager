package io.rileyhe1.concurrency.Data;

import java.util.Map;

/**
 * Data Transfer Object for persisting Download state.
 * Contains only serializable data needed to save and restore downloads.
 */
public class DownloadSnapshot
{
    private String id;
    private String url;
    private String destination;
    private long totalSize;
    private Map<Integer, Long> chunkProgress;
    private String state;

    // No arg constructor for gson deserialization
    public DownloadSnapshot()
    {
    }

    // constructor used when creating snapshots from download instances
    public DownloadSnapshot(String id, String url, String destination, 
                           long totalSize, Map<Integer, Long> chunkProgress, String state)
    {
        this.id = id;
        this.url = url;
        this.destination = destination;
        this.totalSize = totalSize;
        this.chunkProgress = chunkProgress;
        this.state = state;
    }

    // Getters
    public String getId()
    {
        return id;
    }

    public String getUrl()
    {
        return url;
    }

    public String getDestination()
    {
        return destination;
    }

    public long getTotalSize()
    {
        return totalSize;
    }

    public Map<Integer, Long> getChunkProgress()
    {
        return chunkProgress;
    }

    public String getState()
    {
        return state;
    }

    // Setters (needed for Gson deserialization)
    public void setId(String id)
    {
        this.id = id;
    }

    public void setUrl(String url)
    {
        this.url = url;
    }

    public void setDestination(String destination)
    {
        this.destination = destination;
    }

    public void setTotalSize(long totalSize)
    {
        this.totalSize = totalSize;
    }

    public void setChunkProgress(Map<Integer, Long> chunkProgress)
    {
        this.chunkProgress = chunkProgress;
    }

    public void setState(String state)
    {
        this.state = state;
    }
}