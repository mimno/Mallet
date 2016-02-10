package cc.mallet.util;

import java.util.logging.*;

/**
 * Created by IntelliJ IDEA.
 * User: hough
 * Date: Feb 10, 2004
 * Time: 4:37:07 PM
 * To change this template use Options | File Templates.
 */
public class MalletProgressMessageLogger extends MalletLogger{
	protected MalletProgressMessageLogger (String name, String resourceBundleName)
	{
		super (name, resourceBundleName);
	}

	public static Logger getLogger (String name)
	{
		MalletProgressMessageLogger mpml = new MalletProgressMessageLogger(name, null);
		LogManager.getLogManager().addLogger(mpml);
		return mpml;
	}

	public void log(LogRecord logRecord)
	{
		// convert to subclass of logRecord, and pass it on..
		// I'm sure this is losing information...
		//System.out.println("MPML log record entered " +logRecord);
		ProgressMessageLogRecord progressMessageLogRecord = new ProgressMessageLogRecord(logRecord);
        super.log(progressMessageLogRecord);
//
//		//getParent().log(progressMessageLogRecord);
//		//try doing the dispatch ourselves.  Fewer classes to override...
//        // Whole reason for overriding is so we can not send things to console twice
//		// once in progress message; once in parent..
//
//		// I think this is some approximation of what java.util.logger.log() does
//		//todo: add level test and filtering
//
//		Logger currentLogger = this;
//		boolean sentToConsole = false;
//		boolean useParentHandlers = getUseParentHandlers();
//		while (currentLogger != null){
//			Handler[] handlers = currentLogger.getHandlers();
//			for(int i=0; i<handlers.length; i++){
//				if (handlers[i] instanceof ConsoleHandler){
//					if (!sentToConsole){
//						handlers[i].publish(progressMessageLogRecord);
//						sentToConsole = true;
//					}
// 				}else{
//					handlers[i].publish(progressMessageLogRecord);
//				}
//			}
//
//			if (useParentHandlers) {
//				currentLogger = currentLogger.getParent();
//			} else {
//				currentLogger = null;
//			}
//		}

	}

}
