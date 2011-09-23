package org.tmatesoft.svn.core.test;

import java.io.File;
import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.tmatesoft.svn.cli.svn.SVN;
import org.tmatesoft.svn.cli.svnadmin.SVNAdmin;
import org.tmatesoft.svn.cli.svndumpfilter.SVNDumpFilter;
import org.tmatesoft.svn.cli.svnlook.SVNLook;
import org.tmatesoft.svn.cli.svnsync.SVNSync;
import org.tmatesoft.svn.cli.svnversion.SVNVersion;
import org.tmatesoft.svn.core.internal.util.DefaultSVNDebugFormatter;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.util.SVNLogType;

import com.martiansoftware.nailgun.NGContext;

public class NailgunProcessor {
    
    private static volatile String ourLastTestName;

    public static void nailMain(NGContext context) {
        String programName = context.getArgs()[0];
        String[] programArgs = new String[context.getArgs().length - 1];
        System.arraycopy(context.getArgs(), 1, programArgs, 0, programArgs.length);
        String testName = context.getEnv().getProperty("SVN_CURRENT_TEST");
        configureEnvironment(context);
        configureLoggers(testName);
        
        if ("svn".equals(programName)) {
            SVN.main(programArgs);
        } else if ("svnadmin".equals(programName)) {
            SVNAdmin.main(programArgs);
        } else if ("svnlook".equals(programName)) {
            SVNLook.main(programArgs);
        } else if ("svnversion".equals(programName)) {
            SVNVersion.main(programArgs);
        } else if ("svnsync".equals(programName)) {
            SVNSync.main(programArgs);
        } else if ("svndumpfilter".equals(programName)) {
            SVNDumpFilter.main(programArgs);
        } else if ("entries-dump".equals(programName)) {
            // give them some entries dump!
        }
        
    }

    private static void configureLoggers(String testName) {
        synchronized (NailgunProcessor.class) {
            if (testName == null || ourLastTestName != null && ourLastTestName.equals(testName)) {
                return;
            }
            ourLastTestName = testName;
        }
        
        Handler logHandler = null;        
        
        setupLogger(Logger.getLogger(SVNLogType.DEFAULT.getName()));
        setupLogger(Logger.getLogger(SVNLogType.NETWORK.getName()));
        setupLogger(Logger.getLogger(SVNLogType.WC.getName()));
        setupLogger(Logger.getLogger(SVNLogType.CLIENT.getName()));
        setupLogger(Logger.getLogger(SVNLogType.FSFS.getName()));
        
        if (!PythonTests.isLoggingEnabled()) {
            return;
        }
        try {
            logHandler = createTestLogger(testName);
        } catch (IOException e) {
        }
        if (logHandler != null) {
            Logger.getLogger(SVNLogType.DEFAULT.getName()).addHandler(logHandler);
            Logger.getLogger(SVNLogType.NETWORK.getName()).addHandler(logHandler);
            Logger.getLogger(SVNLogType.WC.getName()).addHandler(logHandler);
            Logger.getLogger(SVNLogType.CLIENT.getName()).addHandler(logHandler);
            Logger.getLogger(SVNLogType.FSFS.getName()).addHandler(logHandler);
        }
    }

    private static void configureEnvironment(NGContext context) {
        String editor = context.getEnv().getProperty("SVN_EDITOR");
        String mergeTool = context.getEnv().getProperty("SVN_MERGE");
        String editorFunction = context.getEnv().getProperty("SVNTEST_EDITOR_FUNC");
        String testName = context.getEnv().getProperty("SVN_CURRENT_TEST");

        SVNFileUtil.setTestEnvironment(editor, mergeTool, editorFunction);
        boolean needsToSleep = testName != null && PythonTests.needsSleepForTimestamp(testName);
        SVNFileUtil.setSleepForTimestamp(needsToSleep);
        System.setProperty("user.dir", context.getWorkingDirectory());
    }
    
    private static void setupLogger(Logger logger) {
        logger.setLevel(Level.ALL);
        logger.setUseParentHandlers(false);
        Handler[] existingHandlers = logger.getHandlers();
        for (int i = 0; i < existingHandlers.length; i++) {
            logger.removeHandler(existingHandlers[i]);
            existingHandlers[i].close();
        }
    }
    
    
    private static Handler createTestLogger(String testName) throws IOException {
        File logFile = PythonTests.getLogsDirectory();
        String path = PythonTests.getTestType() + "_" + testName.trim() + ".log"; 
        logFile = new File(logFile, path);
        FileHandler fileHandler = new FileHandler(logFile.getAbsolutePath(), 0, 1, true);
        fileHandler.setLevel(Level.FINEST);
        fileHandler.setFormatter(new DefaultSVNDebugFormatter());
        
        return fileHandler;
    }
}
