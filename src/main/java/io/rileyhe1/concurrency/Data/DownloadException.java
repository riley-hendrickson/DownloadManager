package io.rileyhe1.concurrency.Data;

@SuppressWarnings("unused")
public class DownloadException extends Exception
{
    private final String downloadId;
    private final String url;

    // used when we don't have download context yet
    public DownloadException(String message)
    {
        super(message);
        this.downloadId = null;
        this.url = null;
    }

    // used to wrap an exception in a download exception when we need to catch
    // and rethrow another exception
    public DownloadException(String message, Throwable cause)
    {
        super(message, cause);
        this.downloadId = null;
        this.url = null;
    }

    // used to create a DownloadException with full contexct, we will use this
    // when we have full download details available
    public DownloadException(String message, String downloadId, String url)
    {
        super(message);
        this.downloadId = null;
        this.url = null;
    }

    // used to create a DownloadException with full context + underlying cause
    public DownloadException(String message, Throwable cause, String downloadId, String url)
    {
        super(message, cause);
        this.downloadId = downloadId;
        this.url = url;
    }
    // getters:
    public String getDownloadId()
    {
        return downloadId;
    }

    public String getUrl()
    {
        return url;
    }
    
    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder("DownloadException: ");
        sb.append(getMessage());

        if (downloadId != null)
        {
            sb.append(" [downloadId=").append(downloadId).append("]");
        }

        if (url != null)
        {
            sb.append(" [url=").append(url).append("]");
        }

        if (getCause() != null)
        {
            sb.append(" caused by ").append(getCause().getClass().getSimpleName());
            sb.append(": ").append(getCause().getMessage());
        }

        return sb.toString();
    }
}
