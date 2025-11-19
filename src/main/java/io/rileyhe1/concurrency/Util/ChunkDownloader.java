package io.rileyhe1.concurrency.Util;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;

import io.rileyhe1.concurrency.Data.ChunkResult;
import io.rileyhe1.concurrency.Data.DownloadConfig;

@SuppressWarnings("unused")
public class ChunkDownloader implements Callable<ChunkResult>
{
    // Configuration
    private final String url;
    private final long startByte;
    private final long endByte;
    private final String tempFilePath;
    private final int chunkIndex;
    private final DownloadConfig config;
    private final ProgressTracker progressTracker;

    // State
    private volatile boolean paused = false;
    private volatile boolean cancelled = false;
    private AtomicLong bytesDownloaded = new AtomicLong(0);

    

    public ChunkDownloader(String url, long startByte, long endByte, String tempFilePath, int chunkIndex,
            DownloadConfig config, ProgressTracker progressTracker, AtomicLong bytesDownloaded)
    {
        this.url = url;
        this.startByte = startByte;
        this.endByte = endByte;
        this.tempFilePath = tempFilePath;
        this.chunkIndex = chunkIndex;
        this.config = config;
        this.progressTracker = progressTracker;
        this.paused = paused;
        this.cancelled = cancelled;
        this.bytesDownloaded = bytesDownloaded;
    }

    @Override
    public ChunkResult call()
    {
        Exception failureCause = null;
        for(int attempt = 1; attempt < config.getMaxRetries(); attempt++)
        {
            try
            {
                downloadChunk();
                return ChunkResult.success(tempFilePath, bytesDownloaded.get());
            }
            catch(Exception e)
            {
                // retry logic:
                failureCause = e;
            }
        }
        return ChunkResult.failure(failureCause);
    }

    private void downloadChunk() throws IOException, InterruptedException
    {
        // 1. Open HTTP connection with Range header
        // 2. Open temp file
        // 3. Download loop with pause/cancel checks
        // 4. Close everything
    }

    public void pause()
    {
        paused = true;
    }

    public void resume()
    {
        paused = false;
    }

    public void cancel()
    {
        cancelled = true;
    }

    public long getBytesDownloaded()
    {
        return 0;
    }
}
