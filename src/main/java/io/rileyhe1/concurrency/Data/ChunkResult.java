package io.rileyhe1.concurrency.Data;

public class ChunkResult
{
    private final String tempFilePath;
    private final long bytesDownloaded;
    private final boolean success;
    private final Exception error;
    private final int chunkIndex;

    private ChunkResult(String tempFilePath, long bytesDownloaded, boolean success, Exception error, int chunkIndex)
    {
        this.tempFilePath = tempFilePath;
        this.bytesDownloaded = bytesDownloaded;
        this.success = success;
        this.error = error;
        this.chunkIndex = chunkIndex;
    }
    public String getTempFilePath()
    {
        return tempFilePath;
    }
    public int getChunkIndex()
    {
        return chunkIndex;
    }
    public long getBytesDownloaded()
    {
        return bytesDownloaded;
    }
    public boolean isSuccessFul()
    {
        return success;
    }
    public Exception getError()
    {
        return error;
    }

    public static ChunkResult success(String tempFilePath, long bytesDownloaded, int chunkIndex)
    {
        return new ChunkResult(tempFilePath, bytesDownloaded, true, null, chunkIndex);
    }
    public static ChunkResult failure(Exception error, int chunkIndex)
    {
        return new ChunkResult(null, 0, false, error, chunkIndex);
    }
    public static ChunkResult failure(Exception error, long bytesDownloaded, int chunkIndex)
    {
        return new ChunkResult(null, bytesDownloaded, false, error, chunkIndex);
    }
    public boolean hasError() 
    {
        return this.error != null;
    }
    public String getErrorMessage()
    {
        return this.error != null? error.getMessage() : null; 
    }
}
