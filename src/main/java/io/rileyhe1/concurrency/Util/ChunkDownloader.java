package io.rileyhe1.concurrency.Util;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.Callable;
import java.util.concurrent.StructuredTaskScope.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import io.rileyhe1.concurrency.Data.ChunkResult;
import io.rileyhe1.concurrency.Data.DownloadConfig;

@SuppressWarnings("unused")
public class ChunkDownloader implements Callable<ChunkResult>
{
    // constants
    private static final int TIMEOUT_DURATION = 10000;
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

    

    public ChunkDownloader(String url, long startByte, long endByte, int chunkIndex,
            DownloadConfig config, ProgressTracker progressTracker)
    {
        // validate parameters:
        if(url == null || url.trim().isEmpty())
        {
            throw new IllegalArgumentException("URL cannot be null or empty");
        }
        if(startByte < 0)
        {
            throw new IllegalArgumentException("Start byte cannot be negative");
        }
        if(endByte < startByte)
        {
            throw new IllegalArgumentException("End byte cannot be less than start byte");
        }
        if(config == null)
        {
            throw new IllegalArgumentException("Config cannot be null");
        }
        if(chunkIndex < 0)
        {
            throw new IllegalArgumentException("Chunk index cannot be less than zero");
        }

        // assign fields
        this.url = url;
        this.startByte = startByte;
        this.endByte = endByte;
        this.chunkIndex = chunkIndex;
        this.tempFilePath = config.getTempDirectory() + "/chunk" + this.chunkIndex + ".bin";
        this.config = config;
        this.progressTracker = progressTracker;
        this.bytesDownloaded = new AtomicLong(0);
    }

    @Override
    public ChunkResult call()
    {
        Exception lastException = null;
        for(int attempt = 0; attempt < config.getMaxRetries(); attempt++)
        {
            try
            {
                downloadChunk();
                return ChunkResult.success(tempFilePath, bytesDownloaded.get(), chunkIndex);
            }
            catch(InterruptedException e)
            {
                // thread was interrupted, so we want to fail immediately and not do any retries:
                Thread.currentThread().interrupt();
                return ChunkResult.failure(e, bytesDownloaded.get(), chunkIndex);
            }
            catch(IOException e)
            {
                lastException = e;

                // log the attempt:
                System.err.println("Chunk " + this.chunkIndex + " attempt " + (attempt + 1) + " failed: " + e.getMessage());

                // if this was not the last attempt, wait for a retry:
                if(attempt < config.getMaxRetries() - 1)
                {
                    try
                    {
                        Thread.sleep(config.getRetryDelayMS());
                    }
                    catch(InterruptedException ie)
                    {
                        Thread.currentThread().interrupt();
                        return ChunkResult.failure(ie, bytesDownloaded.get(), chunkIndex);
                    }
                }
            }

        }
        return ChunkResult.failure(lastException, bytesDownloaded.get(), chunkIndex);
    }

    private void downloadChunk() throws IOException, InterruptedException
    {
        // 1. Open HTTP connection with Range header
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        try
        {
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            connection.setRequestProperty("Range", "bytes=" + startByte + "-" + endByte);
            connection.setConnectTimeout(TIMEOUT_DURATION);
            connection.setReadTimeout(TIMEOUT_DURATION);

            try
            {
                connection.connect();
            }
            catch(SocketTimeoutException e)
            {
                System.out.println("Timeout Expired before connection was established");
            }
            catch(IOException e)
            {
                System.out.println("I/O error occurred while establishing connection");
            }

            int responseCode = connection.getResponseCode();
            if(responseCode != HttpURLConnection.HTTP_PARTIAL)
            {
                throw new IOException("Server does not accept range requests, cannot download in chunks. Response: " + responseCode);
            }
            //  Open temp file and input stream from http url connection in try with resources block to ensure they're 
            //  closed when we're done or when we encounter an exception
            try (InputStream inputStream = connection.getInputStream();
                 FileOutputStream outputStream = new FileOutputStream(tempFilePath))
            {
                // 3. Download loop with pause/cancel checks implemented later
                byte[] buffer = new byte[config.getBufferSize()];
                int bytesRead;
                while((bytesRead = inputStream.read(buffer)) != 1)
                {
                    outputStream.write(buffer, 0, bytesRead);
                    this.bytesDownloaded.addAndGet(bytesRead);
                    if(progressTracker != null) progressTracker.updateProgress(chunkIndex, bytesRead);
                }

            }
        // 4. Close everything
        }
        finally
        {
            if(connection != null) connection.disconnect();
        }
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
        return this.bytesDownloaded.get();
    }
}
