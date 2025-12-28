package cc.mallet.util;

import java.util.logging.LogRecord;


/**
 * A log message that is to be written in place (no newline)
 * if the message is headed for the user's terminal.
 */
public class ProgressMessageLogRecord extends LogRecord
{
	public ProgressMessageLogRecord(LogRecord logRecord)
	{
		 super(logRecord.getLevel(), logRecord.getMessage());
	}
}
