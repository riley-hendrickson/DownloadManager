package io.rileyhe1.concurrency.Util;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import io.rileyhe1.concurrency.Data.ChunkResult;
import io.rileyhe1.concurrency.Data.DownloadConfig;
import io.rileyhe1.concurrency.Data.DownloadException;
import io.rileyhe1.concurrency.Data.DownloadState;

public class Download
{
    private String id;
    private String url;
    private String destination;
    private final DownloadConfig config;
    private long totalSize;
    private volatile DownloadState state;
    private List<ChunkDownloader> chunks;
    private List<Future<ChunkResult>> futureResults;
    private List<ChunkResult> results;
    private int numChunks = 0;
    private CountDownLatch completionLatch;
    private volatile boolean completionLatchPulled = false;
    private final ExecutorService executorService;
    private final ProgressTracker progressTracker;

    private Exception error;

    public Download(String url, String destination, DownloadConfig config, ProgressTracker progressTracker, ExecutorService executorService) throws DownloadException
    {
        // validate arguments
        if(url == null || url.trim().isEmpty()) throw new IllegalArgumentException("URL cannot be null or empty!");
        if(destination == null || destination.trim().isEmpty()) throw new IllegalArgumentException("Destination cannot be null or empty!");
        if(config == null) throw new IllegalArgumentException("Config cannot be null!");
        if(progressTracker == null) throw new IllegalArgumentException("Progress Tracker cannot be null!");
        if(executorService == null) throw new IllegalArgumentException("Executor Service cannot be null!");
        // assign unique id for the download
        this.id = UUID.randomUUID().toString();
        // store arguments
        this.url = url;
        this.destination = destination;
        this.config = config;
        this.progressTracker = progressTracker;
        this.executorService = executorService;
        this.state = DownloadState.PENDING;

        // initialize other instance fields:
        this.chunks = new ArrayList<>();
        this.futureResults = new ArrayList<>();
        this.results = new ArrayList<>();

        // attempt an HTTP HEAD request to find the size of the download and to ensure it supports range requests
        HttpURLConnection connection = null;
        try
        {
        connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setRequestMethod("HEAD");
        connection.setConnectTimeout(config.getConnectionTimeout());
        connection.setReadTimeout(config.getReadTimeout());
        // set user-agent header in hopes of deterring server request rejection
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");
        int responseCode = connection.getResponseCode();
        if(responseCode == HttpURLConnection.HTTP_OK)
        {
            String contentLengthHeader = connection.getHeaderField("Content-Length");
            if(contentLengthHeader != null)
            {
                this.totalSize = Long.parseLong(contentLengthHeader);
                if(this.totalSize <= 0)
                {
                    throw new IOException("Invalid Content-Length: " + this.totalSize);
                }
            }
            else throw new IOException("Could not find size of download");
        }
        else throw new IOException("HTTP HEAD request failed with response code: " + responseCode);

        String acceptRanges = connection.getHeaderField("Accept-Ranges");
        boolean supportsRanges = acceptRanges != null && acceptRanges.equalsIgnoreCase("bytes");

        if(!supportsRanges && this.totalSize >= config.getMinSizeForChunking())
        {
            throw new IOException("Server does not support range requests, cannot download in chunks.");
        }
    }
    catch(IOException e)
    {
        throw new DownloadException("Failed to retrieve file metadata from " + url, e, id, url);
    }
    finally
    {
        if(connection != null) connection.disconnect();
    }
}

    public synchronized void start()
    {
        if(state != DownloadState.PENDING) throw new IllegalStateException("Cannot start download: Expected Pending, Was: " + state);
        
        this.state = DownloadState.DOWNLOADING;

        long chunkSize = config.getChunkSize();
        if(totalSize < config.getMinSizeForChunking()) this.numChunks = 1;
        else
        {
            // we want to round up on our division here to make sure we get the final chunk whose length < chunkSize (if it exists)
            this.numChunks = (int) Math.ceil((double) totalSize / chunkSize);
        }

        this.completionLatch = new CountDownLatch(1);

        long startByte = 0, endByte;
        for(int i = 0; i < numChunks; i++)
        {   
            endByte = (i == numChunks - 1) ? totalSize - 1 : startByte + chunkSize - 1;

            ChunkDownloader curChunk = new ChunkDownloader(url, startByte, endByte, i, config, progressTracker);
            chunks.add(curChunk);
            futureResults.add(executorService.submit(curChunk));

            startByte += chunkSize;
        }
        executorService.submit(this::handleChunkCompletion);
    }
    // helper method to ensure start() is non-blocking
    private void handleChunkCompletion()
    {
        try
        {
            // Check if cancelled before processing
            if(state == DownloadState.CANCELLED)
            {
                return;
            }
            // collect all the results
            for(Future<ChunkResult> futureResult : futureResults)
            {
                ChunkResult result = futureResult.get();

                // verify results were successful and handle failures
                if(!result.isSuccessful())
                {
                    throw new IOException("Chunk " + result.getChunkIndex() + " failed: " 
                    + result.getErrorMessage());
                }
                results.add(result);
            }
            // Double-check state before assembling (could have been cancelled during collection)
            if(state == DownloadState.CANCELLED)
            {
                return;
            }
            // assemble the final file
            FileAssembler.assembleChunks(results, destination);
            state = DownloadState.COMPLETED;
        }
        catch(Exception e)
        {
            // we only want to set to failed if the download was not cancelled
            if(state != DownloadState.CANCELLED)
            {
                state = DownloadState.FAILED;
                error = e;
            }
        }
        finally
        {
            if(!completionLatchPulled)
            {
                completionLatchPulled = true;
                completionLatch.countDown();
            }  
        }
    }

    public synchronized void pause()
    {
        if(state == DownloadState.PENDING)
        {
            throw new IllegalStateException("Cannot pause: Download has not been started yet");
        }
        if(state != DownloadState.DOWNLOADING) throw new IllegalStateException("Cannot pause when the download is not downloading");
        state = DownloadState.PAUSED;

        for(ChunkDownloader chunk : chunks)
        {
            chunk.pause();
        }
    }

    public synchronized void resume()
    {
        if(state == DownloadState.PENDING)
        {
            throw new IllegalStateException("Cannot resume: Download has not been started yet");
        }
        if(state != DownloadState.PAUSED) throw new IllegalStateException("Cannot resume when the download is not paused");
        state = DownloadState.DOWNLOADING;

        for(ChunkDownloader chunk : chunks)
        {
            chunk.resume();
        }
    }

    public synchronized void cancel()
    {
        if(state == DownloadState.PENDING)
        {
            throw new IllegalStateException("Cannot cancel: Download has not been started yet");
        }
        // if the download is complete or already cancelled, there is nothing to do.
        if(state == DownloadState.COMPLETED || state == DownloadState.CANCELLED) return;
        state = DownloadState.CANCELLED;

        // cancel all chunks
        for(ChunkDownloader chunk : chunks)
        {
            chunk.cancel();
        }

        // cancel any pending futures:
        for(Future<ChunkResult> future : futureResults)
        {
            future.cancel(true);
        }
        // countdown the latch so awaitCompletion doesn't hang
        if(!completionLatchPulled)
        {
            completionLatchPulled = true;
            completionLatch.countDown();
        } 
    }

    public void awaitCompletion() throws InterruptedException, DownloadException
    {
        completionLatch.await();
        if(state == DownloadState.FAILED && error != null)
        {
            throw new DownloadException("Download failed", error, id, url);
        }
    }

    public double getProgress()
    {
        if(totalSize == 0) return 0.0;
        return progressTracker.getProgressPercentage(totalSize);
    }

    public DownloadState getState()
    {
        return state;
    }

    public Exception getError()
    {
        return this.error;
    }
    public String getId()
    {
        return this.id;
    }
    public long getTotalSize()
    {
        return this.totalSize;
    }
}
