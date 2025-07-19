package com.eric_eldard.ui.log;

/**
 * Log entry data structure representing a single log message with level and content
 */
public record LogEntry(LogLevel level, String message)
{
}