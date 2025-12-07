package io.rileyhe1.concurrency.Util;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import io.rileyhe1.concurrency.Data.ChunkResult;
import io.rileyhe1.concurrency.Data.DownloadConfig;
import io.rileyhe1.concurrency.Data.DownloadException;
import io.rileyhe1.concurrency.Data.DownloadSnapshot;
import io.rileyhe1.concurrency.Data.DownloadState;

public class Download
{
    private String id;
    private String tempDirectory;
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

    private Map<Integer, Long> savedChunkProgress;

    private Exception error;
    // TODO: REMOVE EXECUTOR SERVICE FROM CONSTRUCTOR AND ADJUST USAGE ACCORDINGLY
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
        this.state = DownloadState.PENDING;

        // Create download-specific temp directory
        this.tempDirectory = config.getTempDirectory() + "/" + id;
        Path tempDirPath = Paths.get(tempDirectory);
        
        try
        {
            if(!Files.exists(tempDirPath))
            {
                Files.createDirectories(tempDirPath);
            }
        }
        catch(IOException e)
        {
            throw new IllegalArgumentException("Failed to create temp directory: " + tempDirectory, e);
        }

        // initialize other instance fields:
        this.chunks = new ArrayList<>();
        this.futureResults = new ArrayList<>();
        this.results = new ArrayList<>();
        this.completionLatch = new CountDownLatch(1);

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

        // now that we've determined the file size, we'll compute the number of chunks
        long chunkSize = config.getChunkSize();
        if(totalSize < config.getMinSizeForChunking()) this.numChunks = 1;
        else
        {
            // we want to round up on our division here to make sure we get the final chunk whose length < chunkSize (if it exists)
            this.numChunks = (int) Math.ceil((double) totalSize / chunkSize);
        }
        // each download needs a thread for each chunk, as well as one more for monitoring chunk completion
        this.executorService = Executors.newFixedThreadPool(numChunks + 1);
    }
    // need to remove executor service from constructor and adjust usage accordingly
    // constructor for loading from snapshot
    public Download(DownloadSnapshot snapshot, DownloadConfig config, 
                ProgressTracker progressTracker, ExecutorService executorService) throws DownloadException
    {
        // Basic validation
        if(snapshot == null) 
            throw new IllegalArgumentException("Snapshot cannot be null!");
        if(config == null) 
            throw new IllegalArgumentException("Config cannot be null!");
        if(progressTracker == null) 
            throw new IllegalArgumentException("Progress Tracker cannot be null!");
        if(executorService == null) 
            throw new IllegalArgumentException("Executor Service cannot be null!");
        
        // Restore from snapshot
        this.id = snapshot.getId(); 
        this.url = snapshot.getUrl();
        this.destination = snapshot.getDestination();
        this.totalSize = snapshot.getTotalSize();
        this.savedChunkProgress = snapshot.getChunkProgress();
        this.numChunks = savedChunkProgress.size();
        this.config = config;
        this.progressTracker = progressTracker;
        this.executorService = Executors.newFixedThreadPool(numChunks + 1);
        this.state = DownloadState.PENDING;
        this.tempDirectory = config.getTempDirectory() + "/" + id;

        // Initialize other instance fields:
        this.chunks = new ArrayList<>();
        this.futureResults = new ArrayList<>();
        this.results = new ArrayList<>();
        this.completionLatch = new CountDownLatch(1);
}
    // starts downloading a new download
    public synchronized void start()
    {
        if(state != DownloadState.PENDING) throw new IllegalStateException("Cannot start download: Expected Pending, Was: " + state);
        
        this.state = DownloadState.DOWNLOADING;

        long startByte = 0, endByte, chunkSize = config.getChunkSize();
        for(int i = 0; i < numChunks; i++)
        {   
            endByte = (i == numChunks - 1) ? totalSize - 1 : startByte + chunkSize - 1;

            ChunkDownloader curChunk = new ChunkDownloader(tempDirectory, url, startByte, endByte, 0, i, config, progressTracker);
            chunks.add(curChunk);
            futureResults.add(executorService.submit(curChunk));

            startByte += chunkSize;
        }
        executorService.submit(this::handleChunkCompletion);
    }
    // continues downloading a previously stopped and saved download from where it left off
    public synchronized void startExisting()
    {
        if(state != DownloadState.PENDING) 
            throw new IllegalStateException("Cannot start download: Expected Pending, Was: " + state);
        
        if(savedChunkProgress == null)
        {
            throw new IllegalStateException("Cannot call startExisting on a new download. Use start() instead.");
        }
        
        this.state = DownloadState.DOWNLOADING;

        long startByte = 0, endByte, chunkSize = config.getChunkSize();
        for(int i = 0; i < numChunks; i++)
        {   
            endByte = (i == numChunks - 1) ? totalSize - 1 : startByte + chunkSize - 1;

            // Get saved progress for this chunk (default to 0 if not found)
            long alreadyDownloaded = savedChunkProgress.getOrDefault(i, 0L);

            ChunkDownloader curChunk = new ChunkDownloader(tempDirectory, url, startByte, endByte, alreadyDownloaded, 
                                                        i, config, progressTracker);
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
            // collect all the results
            for(Future<ChunkResult> futureResult : futureResults)
            {
                try
                {
                    ChunkResult result = futureResult.get();

                    // Check if cancelled during collection
                    if(state == DownloadState.CANCELLED)
                    {
                        return;
                    }

                    // verify results were successful and handle failures
                    if(!result.isSuccessful())
                    {
                        throw new IOException("Chunk " + result.getChunkIndex() + " failed: " 
                        + result.getErrorMessage());
                    }
                    results.add(result);
                }
                catch(CancellationException e)
                {
                    // This will trigger when the future calls cancel, which is expected when we try to cancel a download
                    // in this case we just want to return to hit the finally block
                    return;
                }
                catch(InterruptedException e)
                {
                    // This triggers if the thread is interrupted, so we want to restore interrupt status and return to hit the finally block
                    Thread.currentThread().interrupt();
                    return;
                }
            }

            // one final check for cancellation before assembling the final file:
            if(state == DownloadState.CANCELLED)
            {
                return; // Exit to finally block
            }

            // assemble the final file
            FileAssembler.assembleChunks(results, destination);
            cleanupTempFiles();
            synchronized(this)
            {
                if(state != DownloadState.CANCELLED)
                {
                    state = DownloadState.COMPLETED;
                }
            }
        }
        catch(Exception e)
        {
            // we only want to set to failed if the download was not cancelled
            if(state != DownloadState.CANCELLED)
            {
                state = DownloadState.FAILED;
                error = new DownloadException("Download Failed: " + e.getMessage(), e, id, url);
            }
        }
        finally
        {
            synchronized(this)
            {
                if(!completionLatchPulled)
                {
                    completionLatchPulled = true;
                    completionLatch.countDown();
                } 
            } 
        }
    }

    public synchronized void pause()
    {
        if(state == DownloadState.PENDING)
        {
            throw new IllegalStateException("Cannot pause: Download has not been started yet");
        }
        if(state != DownloadState.DOWNLOADING) throw new IllegalStateException("Cannot pause unless the download is in downloading state, current state: " + state);
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

        cleanupTempFiles();
        executorService.shutdownNow();
    }
    // stops a download without deleting its temp files so we can pick it up later
    public synchronized void stop()
    {
        if(state == DownloadState.PENDING)
        {
            throw new IllegalStateException("Cannot stop: Download has not been started yet");
        }
        // if the download is complete, already cancelled, or stopped there is nothing to do.
        if(state == DownloadState.COMPLETED || state == DownloadState.CANCELLED || state == DownloadState.STOPPED) return;
        state = DownloadState.STOPPED;

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
        executorService.shutdownNow();
    }

    public void awaitCompletion() throws InterruptedException, DownloadException
    {
        completionLatch.await();
        if(state == DownloadState.FAILED && error != null)
        {
            throw new DownloadException("Download failed", error, id, url);
        }
    }

    private void cleanupTempFiles()
    {
        // collect all the temp file paths and attempt to delete them
        for(ChunkResult result : results)
        {
            if(result.getTempFilePath() != null)
            {
                try
                {
                    Files.deleteIfExists(Paths.get(result.getTempFilePath()));
                }
                catch(IOException e)
                {
                    // Log but don't fail - cleanup is best-effort
                    System.err.println("Failed to delete temp file for chunk " + result.getChunkIndex() + " in download " + id);
                }
            }
        }
        // delete this download's directory
        try
        {
            Files.deleteIfExists(Paths.get(tempDirectory));
        } catch (IOException e)
        {
            // Log but don't fail - cleanup is best-effort
            System.err.println("Failed to delete parent directory for download " + id);
        }
    }

    public DownloadSnapshot createSnapshot()
    {
        Map<Integer, Long> progress = new HashMap<>();
        for(int i = 0; i < chunks.size(); i++)
        {
            progress.put(i, chunks.get(i).getBytesDownloaded());
        }
        
        return new DownloadSnapshot(
            id,
            url,
            destination,
            totalSize,
            progress,
            state.toString()
        );
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
    public String getDestination()
    {
        return this.destination;
    }
    public String getUrl()
    {
        return url;
    }

    public long getDownloadedBytes()
    {
        return (long) (totalSize * (getProgress() / 100.0));
    }

    public long getRemainingBytes()
    {
        return totalSize - getDownloadedBytes();
    }

    public String getFileName()
    {
        return Paths.get(destination).getFileName().toString();
    }
}
