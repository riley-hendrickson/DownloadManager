package io.rileyhe1.concurrency.Data;

public class DownloadException extends Exception
{
    String downloadId;
    Throwable cause;
}
