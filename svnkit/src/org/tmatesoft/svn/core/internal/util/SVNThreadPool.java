/*
 * ====================================================================
 * Copyright (c) 2004-2009 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.util;

import java.lang.reflect.Method;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNThreadPool {
    
    private static Method ourNewCachedThreadPoolMethod;
    
    static {
        try {
            Class executorsClass = SVNThreadPool.class.getClassLoader().loadClass("java.util.concurrent.Executors");
            if (executorsClass != null) {
                ourNewCachedThreadPoolMethod = executorsClass.getMethod("newCachedThreadPool", null);
            }
        } catch (Throwable e) {
        }
    }

    private Object myExecutorService;  
    private Method mySubmitMethod;
    
    public Object run(Runnable task) {
        Object futureObject = null;
        Object executorService = getExecutorService();
        if (executorService != null) {
            boolean fallBackToThread = false;
            try {
                Method submitMethod = getSubmitMethod();
                if (submitMethod != null) {
                    futureObject = submitMethod.invoke(executorService, new Object[] { task });
                } else {
                    fallBackToThread = true;
                }
            } catch (Throwable th) {
                fallBackToThread = true;
            }
            if (!fallBackToThread) {
                return futureObject;
            }
        }

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
        return null;
    }
    
    public void cancel(Object futureObject) {
        if (futureObject != null) {
            try {
                Method cancelMethod = futureObject.getClass().getMethod("cancel", new Class[] { boolean.class });
                if (cancelMethod != null) {
                    cancelMethod.invoke(futureObject, new Object[] { Boolean.TRUE });
                }
            } catch (Throwable e) {
            }
        }
    }
    
    public synchronized void dispose() {
        if (myExecutorService != null) {
            try {
                Method shutdownMethod = myExecutorService.getClass().getMethod("shutdown", null);
                if (shutdownMethod != null) {
                    shutdownMethod.invoke(myExecutorService, null);
                }

                Method shutdownNowMethod = myExecutorService.getClass().getMethod("shutdownNow", null);
                if (shutdownNowMethod != null) {
                    shutdownNowMethod.invoke(myExecutorService, null);
                }
                myExecutorService = null;
                mySubmitMethod = null;
            } catch (Throwable e) {
            }
        }
    }
    
    private synchronized Method getSubmitMethod() {
        if (mySubmitMethod == null) {
            Object executorService = getExecutorService();
            if (executorService != null) {
                try {
                    mySubmitMethod = executorService.getClass().getMethod("submit", new Class[] { Runnable.class });
                } catch (Throwable e) {
                }
            }
        }
        return mySubmitMethod;
    }
    
    private synchronized Object getExecutorService() {
        if (myExecutorService == null && ourNewCachedThreadPoolMethod != null) {
            try {
                myExecutorService = ourNewCachedThreadPoolMethod.invoke(null, null);
                if (myExecutorService != null) {
                    Method setCorePoolSizeMethod = myExecutorService.getClass().getMethod("setCorePoolSize", new Class[] { int.class });
                    Method setMaximumPoolSize = myExecutorService.getClass().getMethod("setMaximumPoolSize", new Class[] { int.class });
                    if (setCorePoolSizeMethod != null) {
                        setCorePoolSizeMethod.invoke(myExecutorService, new Object[] { new Integer(2) });
                    }
                    
                    if (setMaximumPoolSize != null) {
                        setMaximumPoolSize.invoke(myExecutorService, new Object[] { new Integer(Integer.MAX_VALUE) });
                    }
                }
            } catch (Throwable th) {
            }  
        }
        return myExecutorService;
    }
}
