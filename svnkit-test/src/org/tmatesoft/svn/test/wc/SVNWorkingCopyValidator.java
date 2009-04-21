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
package org.tmatesoft.svn.test.wc;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.test.AbstractSVNTestValidator;
import org.tmatesoft.svn.test.SVNTestErrorCode;
import org.tmatesoft.svn.test.util.SVNTestDebugLog;

/**
 * @author TMate Software Ltd.
 * @version 1.2.0
 */
public class SVNWorkingCopyValidator extends AbstractSVNTestValidator  implements ISVNWorkingCopyWalker {

    
    private ISVNWorkingCopy myWorkingCopy;
    private ISVNWorkingCopy myDescriptor;
    private boolean myWalkWorkingCopy;
    private boolean myCheckExpectedAttributesOnly;

    private Collection myStateConflicts;

    public SVNWorkingCopyValidator(File wc, SVNWCDescriptor descriptor, boolean checkExpectedAttributesOnly) {
        reset(wc, descriptor, checkExpectedAttributesOnly);

        myWalkWorkingCopy = false;
    }

    public void reset(File wc, SVNWCDescriptor descriptor, boolean checkExpectedAttributesOnly) {
        myWorkingCopy = new SVNTestWorkingCopy(wc);
        myDescriptor = descriptor;
        myCheckExpectedAttributesOnly = checkExpectedAttributesOnly;
    }

    private ISVNWorkingCopy getWorkingCopy() {
        return myWorkingCopy;
    }

    private ISVNWorkingCopy getDescriptor() {
        return myDescriptor;
    }

    public boolean doWalkWorkingCopy() {
        return myWalkWorkingCopy;
    }

    public void setWalkWorkingCopy(boolean walkWorkingCopy) {
        myWalkWorkingCopy = walkWorkingCopy;
    }

    public boolean isCheckExpectedAttributesOnly() {
        return myCheckExpectedAttributesOnly;
    }

    public void setCheckExpectedAttributesOnly(boolean checkExpectedAttributesOnly) {
        myCheckExpectedAttributesOnly = checkExpectedAttributesOnly;
    }

    private Collection getStateConflicts() {
        if (myStateConflicts == null) {
            myStateConflicts = new ArrayList();
        }
        return myStateConflicts;
    }

    public void validate() throws SVNException {
        if (doWalkWorkingCopy()) {
            getWorkingCopy().walk(this);
        } else {
            getDescriptor().walk(this);
        }

        if (getStateConflicts().isEmpty()) {
            success();
            return;
        }

        for (Iterator iterator = getStateConflicts().iterator(); iterator.hasNext();) {
            SVNWCStateConflict conflict = (SVNWCStateConflict) iterator.next();
            SVNTestDebugLog.log(conflict.toString());
        }

        fail("Test failed", SVNTestErrorCode.UNKNOWN);
    }

    public void handleEntry(AbstractSVNTestFile file) throws SVNException {
        AbstractSVNTestFile actualFile;
        AbstractSVNTestFile expectedFile;
        if (doWalkWorkingCopy()) {
            actualFile = file;
            expectedFile = getDescriptor().getTestFile(file.getPath());
        } else {
            expectedFile  = file;
            actualFile =getWorkingCopy().getTestFile(file.getPath());
        }

        SVNWCStateConflict conflict = SVNWCStateConflict.create(actualFile, expectedFile, isCheckExpectedAttributesOnly());
        if (conflict != null) {
            getStateConflicts().add(conflict);
        }
    }
}
