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

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.test.SVNTestErrorCode;
import org.tmatesoft.svn.util.SVNLogType;

/**
 * @author TMate Software Ltd.
 * @version 1.2.0
 */
public class SVNWCStateConflict {

    private AbstractSVNTestFile myFile;
    private AbstractSVNTestFile myExpectedFile;
    private Collection myConflictedAttributes;

    public static SVNWCStateConflict create(AbstractSVNTestFile file, AbstractSVNTestFile expectedFile) throws SVNException {
        return create(file, expectedFile, false);
    }

    public static SVNWCStateConflict create(AbstractSVNTestFile file, AbstractSVNTestFile expectedFile, boolean checkExpectedAttributesOnly) throws SVNException {
        if (file == null || expectedFile == null) {
            return null;
        }

        if (!file.getPath().equals(expectedFile.getPath())) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNTestErrorCode.UNKNOWN, "test file path is not expected test file path"), SVNLogType.DEFAULT);
        }

        Collection conflictedAttrs = filterConflicts(file, expectedFile, checkExpectedAttributesOnly);
        if (conflictedAttrs == null || conflictedAttrs.isEmpty()) {
            return null;
        }
        return new SVNWCStateConflict(file, expectedFile, conflictedAttrs);
    }

    private static Collection filterConflicts(AbstractSVNTestFile file, AbstractSVNTestFile expectedFile, boolean checkExpectedOnly) throws SVNException {
        Map conflicts = new SVNHashMap();
        Map attrs = file.getAttributes();
        Map expectedAttrs = expectedFile.getAttributes();

        for (Iterator iterator = expectedAttrs.values().iterator(); iterator.hasNext();) {
            SVNTestFileAttribute expectedAttr = (SVNTestFileAttribute) iterator.next();
            SVNTestFileAttribute attr = (SVNTestFileAttribute) attrs.get(expectedAttr.getName());
            if (!expectedAttr.equals(attr)) {
                conflicts.put(expectedAttr.getName(), new ConflictedAttributePair(attr, expectedAttr));
            }
        }

        if (checkExpectedOnly) {
            return conflicts.values();
        }

        for (Iterator iterator = attrs.values().iterator(); iterator.hasNext();) {
            SVNTestFileAttribute attr = (SVNTestFileAttribute) iterator.next();

            if (conflicts.containsKey(attr.getName())) {
                continue;
            }

            SVNTestFileAttribute expectedAttr = (SVNTestFileAttribute) expectedAttrs.get(attr.getName());
            if (!attr.equals(expectedAttr)) {
                conflicts.put(attr.getName(), new ConflictedAttributePair(attr, expectedAttr));
            }
        }
        return conflicts.values();
    }

    public SVNWCStateConflict(AbstractSVNTestFile file, AbstractSVNTestFile expectedFile, Collection conflictedAttributes) {
        myFile = file;
        myExpectedFile = expectedFile;
        myConflictedAttributes = conflictedAttributes;
    }

    public AbstractSVNTestFile getExpectedFile() {
        return myExpectedFile;
    }

    public AbstractSVNTestFile getFile() {
        return myFile;
    }

    public Collection getConflictedAttributes() {
        return myConflictedAttributes;
    }

    public SVNWCStateConflict reload() throws SVNException {
        return create(getFile().reload(), getExpectedFile().reload());
    }

    public String toString() {
        StringBuffer buffer = new StringBuffer();
        buffer.append("\nWorkng copy state conflict detected on ");
        buffer.append(getFile().getPath());
        buffer.append("\n");
        buffer.append("conflicted file attributes are:\n");
        for (Iterator iterator = getConflictedAttributes().iterator(); iterator.hasNext();) {
            ConflictedAttributePair pair = (ConflictedAttributePair) iterator.next();
            buffer.append(pair.getName());
            buffer.append(": expected = ");
            buffer.append(SVNTestFileAttribute.getAttributeAsString(pair.getName(), pair.getExpectedValue()));
            buffer.append("; actual = ");
            buffer.append(SVNTestFileAttribute.getAttributeAsString(pair.getName(), pair.getValue()));
            buffer.append("\n");
        }
        return buffer.toString();
    }

    public static class ConflictedAttributePair {

        private String myName;
        private Object myValue;
        private Object myExpectedValue;

        private ConflictedAttributePair(SVNTestFileAttribute attr, SVNTestFileAttribute expectedAttr) {
            myName = attr != null ? attr.getName() : expectedAttr.getName();
            myValue = attr == null ? null : attr.getValue();
            myExpectedValue = expectedAttr == null ? null : expectedAttr.getValue();
        }

        public String getName() {
            return myName;
        }

        public Object getValue() {
            return myValue;
        }

        public Object getExpectedValue() {
            return myExpectedValue;
        }
    }
}
