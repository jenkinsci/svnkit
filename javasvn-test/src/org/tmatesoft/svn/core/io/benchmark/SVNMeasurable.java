/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.io.benchmark;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.io.SVNRepository;

abstract class SVNMeasurable implements Runnable {
    
    private SVNRepository myRepository2;
    private SVNRepository myRepository1;
    private int myRunCount;
    private long myTime1;
    private long myTime2;
    private SVNException myError;

    public SVNMeasurable() {
    }
    
    public void setup(SVNRepository r1, SVNRepository r2, int runCount) {
        myRepository1 = r1;
        myRepository2 = r2;
        myRunCount = runCount;
    }
    
    public void run() {
        myTime1 = -1;
        myTime2 = -1;
        myError = null;
        long start = System.currentTimeMillis();
        System.out.print('>');
        System.out.flush();
        for (int i = 0; myRepository1 != null && i < myRunCount; i++) {
            try {
                measure(myRepository1);
            } catch (SVNException e) {
                myError = e;
                return;
            }
            System.out.print('.');
            System.out.flush();
        }
        myTime1 = System.currentTimeMillis() - start;
        start = System.currentTimeMillis();
        System.out.print('>');
        System.out.flush();
        for (int i = 0; myRepository2 != null && i < myRunCount; i++) {
            try {
                measure(myRepository2);
            } catch (SVNException e) {
                myError = e;
                return;
            }
            System.out.print('.');
            System.out.flush();
        }
        myTime2 = System.currentTimeMillis() - start;
    }
    
    public String toString() {
        if (myError != null) {
            return myError.getMessage();
        } else if (myTime1 < 0 || myTime2 < 0) {
            return "measurment was not completed";
        }
        long percent = myTime1/100;
        long percents1 = 100;
        long percents2 = myTime2/percent;
        String name = getName() != null ? getName() : "untitled";
        long avg1 = myTime1/myRunCount;
        long avg2 = myTime2/myRunCount;
        return name + ":\n\t" + myRepository1.getLocation() + ": " + percents1 + "% (total: " + myTime1 + "ms. avg: " + avg1 + "ms.)\n\t" + 
                myRepository2.getLocation() + ": "+ percents2 + " % (total: " + myTime2 + "ms. avg: " + avg2 + "ms.)";
    }
    
    protected abstract void measure(SVNRepository repos) throws SVNException;
    
    protected abstract String getName();
    
    public long getTime1() {
        return myTime1;
    }
    
    public long getTime2() {
        return myTime2;
    }
}