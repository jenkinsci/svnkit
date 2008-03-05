/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.javahl;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNClientImplTracker implements Runnable {
    
    private static ReferenceQueue ourQueue;
    private static Map ourReferences = new HashMap();

    public static void registerClient(SVNClientImpl client) {
        synchronized (SVNClientImplTracker.class) {
            if (ourQueue == null) {
                ourQueue = new ReferenceQueue();
                Thread th = new Thread(new SVNClientImplTracker());
                th.setDaemon(true);
                th.start();
            }
        }
        synchronized (ourReferences) {
            WeakReference ref = new WeakReference(Thread.currentThread(), ourQueue);
            SVNClientImpl oldClient = (SVNClientImpl) ourReferences.put(ref, client);
            if (oldClient != null) {
                oldClient.dispose();
            }
        }
        
    }

    public void run() {
        while(true) {
            Reference reference = null;
            try {
                reference = ourQueue.remove();
            } catch (IllegalArgumentException e) {
            } catch (InterruptedException e) {
            }
            if (reference == null) {
                continue;
            }
            synchronized (ourReferences) {
                SVNClientImpl oldClient = (SVNClientImpl) ourReferences.remove(reference);
                if (oldClient != null) {
                    oldClient.dispose();
                }
            }
        }
    }

}
