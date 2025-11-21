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
            DownloadConfig config, ProgressTracker progressTracker, AtomicLong bytesDownloaded)
    {
        this.url = url;
        this.startByte = startByte;
        this.endByte = endByte;
        this.chunkIndex = chunkIndex;
        this.tempFilePath = config.getTempDirectory() + "/chunk" + this.chunkIndex + ".bin";
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
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        try
        {
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
                throw new IOException("Server does not accept range requests, cannot download in chunks");
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
                    progressTracker.updateProgress(chunkIndex, bytesRead);
                }

            }
        // 4. Close everything
        }
        finally
        {
            connection.disconnect();
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
