package cc.mallet.util;
import java.util.logging.*;
import java.util.Arrays;
/**
 * Format ProgressMessages destined for screen.
 * Progress messages are repetitive messages, of which only
 * the last one is generally of interest.
 * Progress messages are a subclass of LogRecord, generated
 * by a progressMessageLogger.
 * When printing a progress message, we carriage return but
 * supress the line-feed.
 * If we get a message that is not a progressMessage, print
 * it the same way PlainLogFormatter does.
 * todo: capture the formatter that was on the console
 * (usually a plainlogformatter) and defer to it when
 * needed.
 */
public class ProgressMessageLogFormatter extends SimpleFormatter
{
	boolean lastMessageWasProgressMessage=false;
    int lastProgressMessageLength=0;


	public ProgressMessageLogFormatter() {
		super();
	}

	public String format (LogRecord record) {
		int length = record.getMessage().length();
		if (record instanceof ProgressMessageLogRecord){
			String suffix = "";
			if (lastMessageWasProgressMessage && lastProgressMessageLength>length){
				// pad with trailing blanks if previous message was shorter than ours.
				int padding = lastProgressMessageLength-length;
				char []c = new char[padding];
				Arrays.fill(c, ' ');
				suffix = new String(c);
			}
			lastMessageWasProgressMessage = true;
			lastProgressMessageLength = length;
			return record.getMessage() + suffix + "\r";
		}else{
			String prefix = lastMessageWasProgressMessage? "\n" : "";
			lastMessageWasProgressMessage = false;
			return prefix + record.getMessage()+ "\n";
		}
	}
}
