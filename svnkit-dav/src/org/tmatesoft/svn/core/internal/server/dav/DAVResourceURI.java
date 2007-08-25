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
package org.tmatesoft.svn.core.internal.server.dav;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVResourceURI {

    public static final String SPECIAL_URI = "!svn";
    public static final String DEDAULT_VCC_NAME = "default";

    private String myURI;
    private String myContext;
    private DAVResourceType myType;
    private DAVResourceKind myKind;
    private long myRevision;
    private String myPath;
    private String myActivityID;
    private boolean myIsExists = false;
    private boolean myIsVersioned = false;
    private boolean myIsBaseLined = false;
    private boolean myIsWorking = false;


    public DAVResourceURI(String context, String uri, String label, boolean useCheckedIn) throws SVNException {
        myURI = uri;
        myContext = context;
        myRevision = DAVResource.INVALID_REVISION;
        parseURI(label, useCheckedIn);
    }

    public String getRequestURI() {
        return DAVPathUtil.append(getContext(), getURI());
    }

    public String getContext() {
        return myContext;
    }

    public String getURI() {
        return myURI;
    }

    public void setURI(String uri) {
        myURI = uri;
    }

    public DAVResourceType getType() {
        return myType;
    }

    public void setType(DAVResourceType type) {
        myType = type;
    }

    public DAVResourceKind getKind() {
        return myKind;
    }

    public void setKind(DAVResourceKind kind) {
        myKind = kind;
    }

    public long getRevision() {
        return myRevision;
    }

    private void setRevision(long revisionNumber) {
        myRevision = revisionNumber;
    }

    public String getPath() {
        return myPath;
    }

    public void setPath(String path) {
        myPath = DAVPathUtil.standardize(path);
    }

    public String getActivityID() {
        return myActivityID;
    }

    public void setActivityID(String activityID) {
        myActivityID = activityID;
    }

    public boolean exists() {
        return myIsExists;
    }

    private void setExists(boolean isExist) {
        myIsExists = isExist;
    }

    public boolean isVersioned() {
        return myIsVersioned;
    }

    public void setVersioned(boolean isVersioned) {
        myIsVersioned = isVersioned;
    }

    public boolean isBaseLined() {
        return myIsBaseLined;
    }

    public void setBaseLined(boolean isBaseLined) {
        myIsBaseLined = isBaseLined;
    }

    public boolean isWorking() {
        return myIsWorking;
    }

    public void setWorking(boolean isWorking) {
        myIsWorking = isWorking;
    }

    private void parseURI(String label, boolean useCheckedIn) throws SVNException {
        if (!SPECIAL_URI.equals(DAVPathUtil.head(getURI()))) {
            setKind(DAVResourceKind.PUBLIC);
            setType(DAVResourceType.REGULAR);
            setPath(getURI());
            setVersioned(true);
        } else {
            String specialPart = DAVPathUtil.removeHead(getURI(), false);
            if (specialPart.length() == 0) {
                // root/!svn
                setType(DAVResourceType.PRIVATE);
                setKind(DAVResourceKind.ROOT_COLLECTION);
            } else {
                specialPart = DAVPathUtil.dropLeadingSlash(specialPart);
                if (!specialPart.endsWith("/") && SVNPathUtil.getSegmentsCount(specialPart) == 1) {
                    // root/!svn/XXX
                    setType(DAVResourceType.PRIVATE);
                } else {
                    DAVResourceKind kind = DAVResourceKind.parseKind(DAVPathUtil.head(specialPart));
                    if (kind != DAVResourceKind.UNKNOWN) {
                        setKind(kind);
                        String parameter = DAVPathUtil.removeHead(specialPart, false);
                        parameter = DAVPathUtil.dropLeadingSlash(parameter);
                        if (kind == DAVResourceKind.VCC) {
                            parseVCC(parameter, label, useCheckedIn);
                        } else if (kind == DAVResourceKind.VERSION) {
                            parseVersion(parameter);
                        } else if (kind == DAVResourceKind.BASELINE) {
                            parseBaseline(parameter);
                        } else if (kind == DAVResourceKind.BASELINE_COLL) {
                            parseBaselineCollection(parameter);
                        } else if (kind == DAVResourceKind.ACT_COLLECTION) {
                            parseActivity(parameter);
                        } else if (kind == DAVResourceKind.HISTORY) {
                            parseHistory(parameter);
                        } else if (kind == DAVResourceKind.WRK_BASELINE) {
                            parseWorkingBaseline(parameter);
                        } else if (kind == DAVResourceKind.WORKING) {
                            parseWorking(parameter);
                        }
                    }
                }
            }
        }
    }

    private void parseWorking(String parameter) {
        setType(DAVResourceType.WORKING);
        setVersioned(true);
        setWorking(true);
        if (SVNPathUtil.getSegmentsCount(parameter) == 1) {
            setActivityID(parameter);
            setPath("/");
        } else {
            setActivityID(DAVPathUtil.head(parameter));
            setPath(DAVPathUtil.removeHead(parameter, false));
        }
    }

    private void parseWorkingBaseline(String parameter) throws SVNException {
        setType(DAVResourceType.WORKING);
        setWorking(true);
        setVersioned(true);
        setBaseLined(true);
        if (SVNPathUtil.getSegmentsCount(parameter) == 1) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_ILLEGAL_URL, "Invalid URI ''{0}''", getRequestURI()));
        }
        setActivityID(DAVPathUtil.head(parameter));
        try {
            String revisionParameter = DAVPathUtil.removeHead(parameter, false);
            long revision = Long.parseLong(DAVPathUtil.dropLeadingSlash(revisionParameter));
            setRevision(revision);
        } catch (NumberFormatException e) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_ILLEGAL_URL, e), e);
        }
    }

    private void parseHistory(String parameter) {
        setType(DAVResourceType.HISTORY);
        setPath(parameter);
    }

    private void parseActivity(String parameter) {
        setType(DAVResourceType.ACTIVITY);
        setActivityID(parameter);
    }

    private void parseBaselineCollection(String parameter) throws SVNException {
        long revision = DAVResource.INVALID_REVISION;
        String parameterPath;
        if (SVNPathUtil.getSegmentsCount(parameter) == 1) {
            parameterPath = "/";
            try {
                revision = Long.parseLong(parameter);
            } catch (NumberFormatException e) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_ILLEGAL_URL, e.getMessage()), e);
            }
        } else {
            try {
                revision = Long.parseLong(DAVPathUtil.head(parameter));
            } catch (NumberFormatException e) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_ILLEGAL_URL, e.getMessage()), e);
            }
            parameterPath = DAVPathUtil.removeHead(parameter, false);
        }
        setType(DAVResourceType.REGULAR);
        setVersioned(true);
        setRevision(revision);
        setPath(parameterPath);
    }

    private void parseBaseline(String parameter) throws SVNException {
        try {
            setRevision(Long.parseLong(parameter));
        } catch (NumberFormatException e) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_ILLEGAL_URL, e.getMessage()), e);
        }
        setVersioned(true);
        setBaseLined(true);
        setType(DAVResourceType.VERSION);
    }

    private void parseVersion(String parameter) throws SVNException {
        setVersioned(true);
        setType(DAVResourceType.VERSION);
        if (SVNPathUtil.getSegmentsCount(parameter) == 1) {
            try {
                setRevision(Long.parseLong(parameter));
            } catch (NumberFormatException e) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_ILLEGAL_URL, "Invalid URI ''{0}''", e.getMessage()), e);
            }
            setPath("/");
        } else {
            try {
                setRevision(Long.parseLong(DAVPathUtil.head(parameter)));
            } catch (NumberFormatException e) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_ILLEGAL_URL, e.getMessage()), e);
            }
            setPath(DAVPathUtil.removeHead(parameter, false));
        }
    }

    private void parseVCC(String parameter, String label, boolean useCheckedIn) throws SVNException {
        if (!DEDAULT_VCC_NAME.equals(parameter)) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, "Invalid VCC name ''{0}''", parameter));
        }
        if (label == null && !useCheckedIn) {
            setType(DAVResourceType.PRIVATE);
            setExists(true);
            setVersioned(true);
            setBaseLined(true);
        } else {
            long revision = DAVResource.INVALID_REVISION;
            if (label != null) {
                try {
                    revision = Long.parseLong(label);
                } catch (NumberFormatException e) {
                    SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_ILLEGAL_URL, "Invalid label header ''{0}''", label));
                }
            }
            setType(DAVResourceType.VERSION);
            setRevision(revision);
            setVersioned(true);
            setBaseLined(true);
            setPath(null);
        }
    }
}
