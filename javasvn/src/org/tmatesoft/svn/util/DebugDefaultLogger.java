package org.tmatesoft.svn.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * @author Marc Strapetz
 */
public class DebugDefaultLogger implements DebugLogger, LoggingStreamLogger {

	private static final DateFormat ourDateFormat = new SimpleDateFormat(
			"yyyy-MM-dd' 'HH:mm:ss.SSS");

	static {
		try {
			Logger.getLogger("svn").setUseParentHandlers(false);
			SimpleFormatter f = new SimpleFormatter() {
				public synchronized String format(LogRecord record) {
					StringBuffer sb = new StringBuffer();
					String message = formatMessage(record);
					sb.append(ourDateFormat
							.format(new Date(record.getMillis())));
					sb.append(": ");
					sb.append(message);
					sb.append(System.getProperty("line.separator"));
					if (record.getThrown() != null) {
						try {
							StringWriter sw = new StringWriter();
							PrintWriter pw = new PrintWriter(sw);
							record.getThrown().printStackTrace(pw);
							pw.close();
							sb.append(sw.toString());
						} catch (Exception e) {
						}
					}
					return sb.toString();
				}
			};
			String levelStr = System.getProperty("javasvn.log.level", "OFF");
            Level level;
			try {
				level = Level.parse(levelStr);
			} catch (Throwable th) {
                th.printStackTrace();
                level = DebugLog.isSafeModeDefault() ? Level.FINEST
						: Level.FINEST;
			}
			if (isFileLoggingEnabled()) {
				String path = System.getProperty("javasvn.log.path");
				if (path == null) {
					path = "%home%/.javasvn/.javasvn.%g.%u.log";
				}
				path = path.replace(File.separatorChar, '/');
				path = path.replaceAll("%home%", System
						.getProperty("user.home").replace(File.separatorChar,
								'/'));
				path = path.replace('/', File.separatorChar);
				File dir = new File(path).getParentFile();
				if (!dir.exists()) {
					dir.mkdirs();
				}

				Handler handler = new FileHandler(path, 1024 * 1024, 10, true);
				handler.setFormatter(f);
				handler.setLevel(level);
				Logger.getLogger("svn").addHandler(handler);
			}
			if (Boolean.getBoolean("javasvn.log.console")) {
				ConsoleHandler cHandler = new ConsoleHandler();
				cHandler.setLevel(level);
				cHandler.setFilter(null);
				cHandler.setFormatter(f);
				Logger.getLogger("svn").addHandler(cHandler);
			}
			Logger.getLogger("svn").setLevel(level);
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public boolean isFineEnabled() {
		return true;
	}

	public void logFine(String message) {
		Logger.getLogger("svn").log(Level.FINE, message);
	}

	public boolean isInfoEnabled() {
		return true;
	}

	public void logInfo(String message) {
		Logger.getLogger("svn").log(Level.INFO, message);
	}

	public boolean isErrorEnabled() {
		return true;
	}

	public void logError(String message, Throwable th) {
		if (th == null) {
			Logger.getLogger("svn").log(Level.SEVERE, message);
		} else {
			Logger.getLogger("svn").log(Level.SEVERE, message, th);
		}
	}

	public LoggingInputStream getLoggingInputStream(String protocol, InputStream stream) {
		protocol = protocol == null ? "svn" : protocol;
		final boolean enabled = Boolean.getBoolean("javasvn.log." + protocol);
		return new LoggingInputStream(stream, enabled ? this : null);
	}

	public LoggingOutputStream getLoggingOutputStream(String protocol, OutputStream stream) {
		protocol = protocol == null ? "svn" : protocol;
		final boolean enabled = Boolean.getBoolean("javasvn.log." + protocol);
		return new LoggingOutputStream(stream, enabled ? this : null);
	}

	public void logStream(String content, boolean writeNotRead) {
		final String prefix = writeNotRead ? "SVN.SENT: " : "SVN.READ: ";
		content = content.replaceAll("\n", "\\\\n");
		content = content.replaceAll("\r", "\\\\r");
		logFine(prefix + content);
	}

	private static boolean isFileLoggingEnabled() {
		if (System.getProperty("javasvn.log.file") == null) {
			return true;
		}
		return Boolean.getBoolean("javasvn.log.file");
	}
}