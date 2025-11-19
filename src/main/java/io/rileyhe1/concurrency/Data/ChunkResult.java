package io.rileyhe1.concurrency.Data;

public class ChunkResult
{
    private final String tempFilePath;
    private final long bytesDownloaded;
    private final boolean success;
    private final Exception error;

    private ChunkResult(String tempFilePath, long bytesDownloaded, boolean success, Exception error)
    {
        this.tempFilePath = tempFilePath;
        this.bytesDownloaded = bytesDownloaded;
        this.success = success;
        this.error = error;
    }
    public String getTempFilePath()
    {
        return tempFilePath;
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

    public static ChunkResult success(String tempFilePath, long bytesDownloaded)
    {
        return new ChunkResult(tempFilePath, bytesDownloaded, true, null);
    }
    public static ChunkResult failure(Exception error)
    {
        return new ChunkResult(null, 0, false, error);
    }
    public static ChunkResult failure(Exception error, long bytesDownloaded)
    {
        return new ChunkResult(null, bytesDownloaded, false, error);
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
