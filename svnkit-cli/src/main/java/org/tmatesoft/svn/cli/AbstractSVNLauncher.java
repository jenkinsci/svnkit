/*
 * ====================================================================
 * Copyright (c) 2004-2012 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.cli;

import java.text.MessageFormat;

import org.tmatesoft.svn.core.SVNAuthenticationException;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public abstract class AbstractSVNLauncher {

    private static volatile boolean ourIsCompleted;
    private static volatile Thread ourShutdownHook;

    protected void run(String[] args) {
        ourIsCompleted = false;
        
        if (needArgs() && (args == null || args.length < 1)) {
            printBasicUsage();
            failure();
            return;
        }
        registerOptions();
        registerCommands();

        SVNCommandLine commandLine = new SVNCommandLine(needCommand());
        try {
            commandLine.init(args);
        } catch (SVNException e) {
            handleError(e);
            printBasicUsage();
            failure();
            return;
        }
        AbstractSVNCommandEnvironment env = createCommandEnvironment();
        synchronized(AbstractSVNLauncher.class) {
            if (ourShutdownHook == null) {
                ourShutdownHook = new Thread(new Cancellator(env)); 
            } else {
                Runtime.getRuntime().removeShutdownHook(ourShutdownHook);
            }
            Runtime.getRuntime().addShutdownHook(ourShutdownHook);
        }
        
        try {
            try {
                env.init(commandLine);
            } catch (SVNException e) {
                handleError(e);
                if (e instanceof SVNCancelException || e instanceof SVNAuthenticationException) {
                    env.dispose();
                    failure();
                    return;
                }
                printBasicUsage();
                env.dispose();
                failure();
                return;
            }
    
            env.initClientManager();
            if (!env.run()) {
                env.dispose();
                failure();
                return;
            }
            env.dispose();
            success();
        } catch (Throwable th) {
            SVNDebugLog.getDefaultLog().logSevere(SVNLogType.CLIENT, th);            
            th.printStackTrace();
            if (env != null) {
                env.dispose();
            }
            failure();
        } finally {
            setCompleted();
        }
    }

    protected abstract boolean needArgs();

    protected abstract boolean needCommand();

    protected abstract String getProgramName();

    protected abstract AbstractSVNCommandEnvironment createCommandEnvironment();
    
    protected void printBasicUsage() {
        System.err.println(MessageFormat.format("Type ''{0} help'' for usage.", new Object[] {getProgramName()}));
    }

    protected abstract void registerCommands();

    protected abstract void registerOptions();

    public void handleError(SVNException e) {
        System.err.println(e.getMessage());
    }

    public void failure() {
        setCompleted();
        try {
            System.exit(1);
        } catch (SecurityException se) {
            
        }
    }

    public void success() {
        setCompleted();
        try {
            System.exit(0);
        } catch (SecurityException se) {
            
        }
    }
    
    private void setCompleted() {
        synchronized (AbstractSVNLauncher.class) {
            ourIsCompleted = true;
            AbstractSVNLauncher.class.notifyAll();
        }
    }
    
    private class Cancellator implements Runnable {
        
        private AbstractSVNCommandEnvironment myEnvironment;

        public Cancellator(AbstractSVNCommandEnvironment env) {
            myEnvironment = env;
        }
        
        public void run() {
            myEnvironment.setCancelled();
            synchronized (AbstractSVNLauncher.class) {
                while(!ourIsCompleted) {
                    try {
                        AbstractSVNLauncher.class.wait();
                    } catch (InterruptedException e) {
                    }
                }
            }
        }
    }


}
