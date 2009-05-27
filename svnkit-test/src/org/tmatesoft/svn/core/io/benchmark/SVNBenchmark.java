/*
 * ====================================================================
 * Copyright (c) 2004-2009 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.io.benchmark;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import org.tmatesoft.svn.core.io.SVNRepository;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNBenchmark implements Runnable {
    
    public static final Class LOG_ROOT = SVNLogRoot.class;
    public static final Class LOG_PATH = SVNLogPath.class;
    public static final Class CHECKOUT_ROOT = SVNCheckoutRoot.class;
    
    private static final int DEFAULT_RUN_COUNT = 10;
    
    private SVNRepository myRepository1;
    private SVNRepository myRepository2;
    private int myRunCount;

    private Collection myMeasurments;
    
    public SVNBenchmark(SVNRepository r1, SVNRepository r2, int runCount) {
        myMeasurments = new ArrayList();
        setup(r1, r2, runCount);
    }

    public void setup(SVNRepository r1, SVNRepository r2, int runCount) {
        myRepository1 = r1;
        myRepository2 = r2;
        myRunCount = runCount <= 0 ? DEFAULT_RUN_COUNT : runCount;
    }

    public void run() {
        for (Iterator objects = myMeasurments.iterator(); objects.hasNext();) {
            SVNMeasurable measurable = (SVNMeasurable) objects.next();
            measurable.setup(myRepository1, myRepository2, myRunCount);
            try {
                measurable.run();
            } catch (Throwable th) {
                th.printStackTrace();
            } finally {
                System.out.println();
                System.out.println(measurable.toString());
                System.out.flush();
            }
        }
    }
    
    public void clear() {
        myMeasurments.clear();
    }

    public void addMeasurment(Class svnMeasurableClass) {
        SVNMeasurable object = null;
        try {
            object = (SVNMeasurable) svnMeasurableClass.newInstance();
            if (object != null) {
                myMeasurments.add(object);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        } 
    }

    public void addMeasurment(SVNMeasurable svnMeasurable) {
        if (svnMeasurable != null) {
            myMeasurments.add(svnMeasurable);
        }
    }

}
