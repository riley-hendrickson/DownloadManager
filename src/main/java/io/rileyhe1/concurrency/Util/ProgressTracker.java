package io.rileyhe1.concurrency.Util;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@SuppressWarnings("unused")
public class ProgressTracker
{
    private Map<String, AtomicLong> chunkProgress;
    
    public void updateProgress(String chunkId, long bytes)
    {

    }

    public long getTotalProgress()
    {
        return 0;
    }

    public double getProgressPercentage(long totalSize)
    {
        return 0.0;
    }
}
