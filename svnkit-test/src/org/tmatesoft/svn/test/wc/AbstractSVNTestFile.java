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
import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.Map;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;

/**
 * @author TMate Software Ltd.
 * @version 1.3
 */
public abstract class AbstractSVNTestFile {

    private String myPath;
    private SVNFileType myFileType;
    private String myLinkTarget;
    private byte[] myContent;
    private boolean myVersioned;

    private SVNNodeKind myNodeKind;
    private SVNProperties myBaseProperties;
    private SVNProperties myProperties;
    private String mySchedule;
    private boolean myConflicted;

    private SVNURL myCopyFromLocation;
    private long myCopyFromRevision;

    private Map myAttributes;

    public AbstractSVNTestFile(AbstractSVNTestFile file) {
        myPath = file.getPath();
        myFileType = file.getFileType();
        myContent = file.getContent();
        myLinkTarget = file.getLinkTarget();
        myNodeKind = file.getNodeKind();
        myBaseProperties = file.getBaseProperties();
        myProperties = file.getProperties();
        myConflicted = file.isConflicted();
        myCopyFromLocation = file.getCopyFromLocation();
        myCopyFromRevision = file.getCopyFromRevision();
        myVersioned = false;

        mySchedule = file.getSchedule();

        myAttributes = new SVNHashMap(file.getAttributes());
    }

    public AbstractSVNTestFile(String path, SVNFileType fileType, byte[] content) {
        setPath(path);
        setFileType(fileType);
        setContent(content);
        myLinkTarget = null;
        myBaseProperties = null;
        myProperties = null;
        myConflicted = false;
        mySchedule = null;
        myCopyFromLocation = null;
        myCopyFromRevision = -1;
        myVersioned = false;
    }

    public AbstractSVNTestFile(String path) {
        this(path, SVNFileType.DIRECTORY, null);
    }

    public AbstractSVNTestFile(String path, byte[] content) {
        this(path, SVNFileType.FILE, content);
    }

    public AbstractSVNTestFile(String path, String content) {
        this(path, (byte[]) null);
        byte[] bytes = null;
        if (content != null) {
            try {
                bytes = content.getBytes("UTF-8");
            } catch (UnsupportedEncodingException e) {
                bytes = content.getBytes();
            }
        }
        setContent(bytes);
    }

    protected void setDefaults() {
        setBaseProperties(null);
        setProperties(null);
        setConflicted(false);
        setReplaced(false);
        setVersioned(false);
        setDeleted(false);
        setReplaced(false);
        setAdded(false);
        setCopyFromLocation(null);
        setCopyFromRevision(-1);
    }

    public String getPath() {
        return myPath;
    }

    protected void setPath(String path) {
        myPath = SVNPathUtil.canonicalizePath(path);
        addAttribute(SVNTestFileAttribute.PATH_ATTRIBUTE, path);
    }

    public String getLinkTarget() {
        return myLinkTarget;
    }

    public void setLinkTarget(String linkTarget) {
        myLinkTarget = linkTarget;
        addAttribute(SVNTestFileAttribute.LINK_TARGET_ATTRIBUTE, linkTarget);
    }

    public byte[] getContent() {
        return myContent;
    }

    protected void setContent(byte[] content) {
        myContent = content;
        addAttribute(SVNTestFileAttribute.CONTENT_ATTRIBUTE, content);
    }

    public SVNFileType getFileType() {
        return myFileType;
    }

    public void setFileType(SVNFileType fileType) {
        myFileType = fileType;
        addAttribute(SVNTestFileAttribute.FILE_TYPE_ATTRIBUTE, fileType);
    }

    public SVNNodeKind getNodeKind() {
        return myNodeKind;
    }

    public void setNodeKind(SVNNodeKind nodeKind) {
        myNodeKind = nodeKind;
        addAttribute(SVNTestFileAttribute.VERSIONED_NODE_KIND_ATTRIBUTE, nodeKind);
    }

    public boolean isVersioned() {
        return myVersioned;
    }

    public void setVersioned(boolean versioned) {
        myVersioned = versioned;
        addAttribute(SVNTestFileAttribute.VERSIONED_ATTRIBUTE, Boolean.valueOf(versioned));
    }

    public SVNProperties getBaseProperties() {
        return myBaseProperties;
    }

    public void setBaseProperties(SVNProperties baseProperties) {
        myBaseProperties = baseProperties;
        addAttribute(SVNTestFileAttribute.BASE_PROPERTIES_ATTRIBUTE, baseProperties);
    }

    public SVNProperties getProperties() {
        return myProperties;
    }

    public void setProperties(SVNProperties properties) {
        myProperties = properties;
        addAttribute(SVNTestFileAttribute.PROPERTIES_ATTRIBUTE, properties);
    }

    public boolean isConflicted() {
        return myConflicted;
    }

    public void setConflicted(boolean isConflicted) {
        myConflicted = isConflicted;
        addAttribute(SVNTestFileAttribute.CONFLICTED_ATTRIBUTE, Boolean.valueOf(isConflicted));
    }

    public boolean isReplaced() {
        return SVNProperty.SCHEDULE.equals(getSchedule());
    }

    public void setReplaced(boolean replaced) {
        if (replaced) {
            setSchedule(SVNProperty.SCHEDULE_REPLACE);
        } else {
            setSchedule(null);
        }
    }

    public boolean isDeleted() {
        return SVNProperty.SCHEDULE_DELETE.equals(getSchedule());
    }

    public void setDeleted(boolean deleted) {
        if (deleted) {
            setSchedule(SVNProperty.SCHEDULE_DELETE);
        } else {
            setSchedule(null);
        }
    }

    public boolean isAdded() {
        return SVNProperty.SCHEDULE_ADD.equals(getSchedule());
    }

    public void setAdded(boolean added) {
        if (added) {
            setSchedule(SVNProperty.SCHEDULE_ADD);
        } else {
            setSchedule(null);
        }
    }

    public String getSchedule() {
        return mySchedule;
    }

    protected void setSchedule(String schedule) {
        mySchedule = schedule;
        addAttribute(SVNTestFileAttribute.SCHEDULE_ATTRIBUTE, schedule);
    }

    public SVNURL getCopyFromLocation() {
        return myCopyFromLocation;
    }

    public void setCopyFromLocation(SVNURL copyFromLocation) {
        myCopyFromLocation = copyFromLocation;
        addAttribute(SVNTestFileAttribute.COPY_FROM_LOCATION_ATTRIBUTE, copyFromLocation);
    }

    public long getCopyFromRevision() {
        return myCopyFromRevision;
    }

    public void setCopyFromRevision(long copyFromRevision) {
        myCopyFromRevision = copyFromRevision;
        addAttribute(SVNTestFileAttribute.COPY_FROM_REVISION_ATTRIBUTE, new Long(copyFromRevision));
    }

    public Map getAttributes() {
        if (myAttributes == null) {
            myAttributes = new SVNHashMap();
        }
        return Collections.unmodifiableMap(myAttributes);
    }

    public void addAttribute(String name, Object value) {
        if (myAttributes == null) {
            myAttributes = new SVNHashMap();
        }
        myAttributes.put(name, new SVNTestFileAttribute(name, value));
    }

    public abstract AbstractSVNTestFile reload() throws SVNException;

    public abstract AbstractSVNTestFile dump(File workingCopyRoot) throws SVNException;

    public SVNWCStateConflict compare(AbstractSVNTestFile file) throws SVNException {
        return SVNWCStateConflict.create(this, file);
    }
}
