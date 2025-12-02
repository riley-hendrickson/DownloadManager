package io.rileyhe1.concurrency.Util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@SuppressWarnings("unused")
public class ProgressTracker
{
    private final ConcurrentHashMap<Integer, AtomicLong> chunkProgress;
    private long totalSize;
    
    public ProgressTracker(long totalSize)
    {
        this.chunkProgress = new ConcurrentHashMap<>();
        this.totalSize = totalSize;
    }

    public void updateProgress(Integer chunkIndex, long bytes)
    {
        if(chunkProgress.contains(chunkIndex))
        {
            chunkProgress.get(chunkIndex).addAndGet(bytes);
        }
        else
        {
            chunkProgress.put(chunkIndex, new AtomicLong(bytes));
        }
    }

    public long getTotalProgress()
    {
        return chunkProgress.values().stream()
                .mapToLong(AtomicLong::get)
                .sum();
    }

    public double getProgressPercentage(long totalSize)
    {
        return (double) getTotalProgress() / totalSize * 100;
    }
}
