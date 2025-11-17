package io.rileyhe1.concurrency.Util;

import java.util.concurrent.atomic.AtomicLong;

import io.rileyhe1.concurrency.Data.ChunkResult;
@SuppressWarnings("unused")
public class ChunkDownloader
{
    private String url;
    private long startByte;
    private long endByte;
    private String tempFilePath;
    private AtomicLong bytesDownloaded;
    private volatile boolean paused;
    private volatile boolean cancelled;

    public ChunkResult call()
    {
        return null;
    }

    private void downloadChunk()
    {

    }

    public void pause()
    {

    }

    public void resume()
    {

    }

    public long getBytesDownloaded()
    {
        return 0;
    }
}
