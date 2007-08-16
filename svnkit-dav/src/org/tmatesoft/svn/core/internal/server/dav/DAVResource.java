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

import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.io.SVNRepository;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVResource {

    private static final long INVALID_REVISION = SVNRepository.INVALID_REVISION;

    public static final String SPECIAL_URI = "!svn";
    public static final String DEDAULT_VCC_NAME = "default";

    public static final String DEFAULT_COLLECTION_CONTENT_TYPE = "text/html; charset=utf-8";
    public static final String DEFAULT_FILE_CONTENT_TYPE = "text/plain";

    public static final int DAV_RESOURCE_TYPE_REGULAR = -3;
    public static final int DAV_RESOURCE_TYPE_WORKING = -2;
    public static final int DAV_RESOURCE_TYPE_VERSION = -1;
    public static final int DAV_RESOURCE_TYPE_PRIVATE = 1;
    public static final int DAV_RESOURCE_TYPE_ACTIVITY = 2;
    public static final int DAV_RESOURCE_TYPE_HISTORY = 3;

    private String myContext;
    private String myURI;
    private SVNRepository myRepository;
    private int myType;
    private DAVResourceKind myKind;
    private long myRevision;
    private String myPath;
    private String myActivityID;
    private boolean myIsExists = false;
    private boolean myIsCollection;
    private boolean myIsVersioned = false;
    private boolean myIsBaseLined = false;
    private boolean myIsWorking = false;

    private boolean myIsSVNClient;

    private Map mySVNProperties;
    private Collection myEntries;

    /**
     * DAVResource  constructor
     *
     * @param repository   repository resource connect to
     * @param context      contains requested url requestContext and name of repository if servlet use SVNParentPath directive.
     * @param uri          special uri for DAV requests can be /path or /SPECIAL_URI/xxx/path
     * @param label        request's label header
     * @param useCheckedIn special case for VCC resource
     * @throws SVNException if an error occurs while fetching repository properties.
     */
    public DAVResource(SVNRepository repository, String context, String uri, String label, boolean useCheckedIn) throws SVNException {
        myRepository = repository;
        myContext = context;
        myURI = uri;
        myRevision = INVALID_REVISION;
        parseURI(label, useCheckedIn);
        prepare();
    }

    public String getContext() {
        return myContext;
    }

    public String getURI() {
        return myURI;
    }

    private void setURI(String uri) {
        myURI = uri;
    }

    public SVNRepository getRepository() {
        return myRepository;
    }

    public int getType() {
        return myType;
    }

    private void setType(int type) {
        myType = type;
    }

    public DAVResourceKind getKind() {
        return myKind;
    }

    private void setKind(DAVResourceKind kind) {
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

    private void setPath(String path) {
        myPath = DAVPathUtil.standardize(path);
    }

    public String getActivityID() {
        return myActivityID;
    }

    private void setActivityID(String activityID) {
        myActivityID = activityID;
    }

    public boolean exists() {
        return myIsExists;
    }

    private void setExists(boolean isExist) {
        myIsExists = isExist;
    }

    public boolean isCollection() {
        return myIsCollection;
    }

    private void setCollection(boolean isCollection) {
        myIsCollection = isCollection;
    }

    public boolean isVersioned() {
        return myIsVersioned;
    }

    private void setVersioned(boolean isVersioned) {
        myIsVersioned = isVersioned;
    }

    public boolean isBaseLined() {
        return myIsBaseLined;
    }

    private void setBaseLined(boolean isBaseLined) {
        myIsBaseLined = isBaseLined;
    }

    public boolean isWorking() {
        return myIsWorking;
    }

    private void setWorking(boolean isWorking) {
        myIsWorking = isWorking;
    }

    public boolean isSVNClient() {
        return myIsSVNClient;
    }

    public void setSVNClient(boolean isSVNClient) {
        myIsSVNClient = isSVNClient;
    }

    private Map getSVNProperties() {
        if (mySVNProperties == null) {
            mySVNProperties = new HashMap();
        }
        return mySVNProperties;
    }

    public Collection getEntries() {
        if (myEntries == null) {
            myEntries = new ArrayList();
        }
        return myEntries;
    }

    private void parseURI(String label, boolean useCheckedIn) throws SVNException {
        if (!SPECIAL_URI.equals(DAVPathUtil.head(getURI()))) {
            setKind(DAVResourceKind.PUBLIC);
            setType(DAVResource.DAV_RESOURCE_TYPE_REGULAR);
            setPath(getURI());
            setVersioned(true);
        } else {
            String specialPart = DAVPathUtil.removeHead(getURI(), false);
            if (specialPart.length() == 0) {
                // root/!svn
                setType(DAVResource.DAV_RESOURCE_TYPE_PRIVATE);
                setKind(DAVResourceKind.ROOT_COLLECTION);
            } else {
                specialPart = DAVPathUtil.dropLeadingSlash(specialPart);
                if (!specialPart.endsWith("/") && SVNPathUtil.getSegmentsCount(specialPart) == 1) {
                    // root/!svn/XXX
                    setType(DAVResource.DAV_RESOURCE_TYPE_PRIVATE);
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
        setType(DAVResource.DAV_RESOURCE_TYPE_WORKING);
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
        setType(DAVResource.DAV_RESOURCE_TYPE_WORKING);
        setWorking(true);
        setVersioned(true);
        setBaseLined(true);
        if (SVNPathUtil.getSegmentsCount(parameter) == 1) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_ILLEGAL_URL, "Invalid URI ''{0}''", getContext() + getURI()));
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
        setType(DAVResource.DAV_RESOURCE_TYPE_HISTORY);
        setPath(parameter);
    }

    private void parseActivity(String parameter) {
        setType(DAVResource.DAV_RESOURCE_TYPE_ACTIVITY);
        setActivityID(parameter);
    }

    private void parseBaselineCollection(String parameter) throws SVNException {
        long revision = INVALID_REVISION;
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
        setType(DAVResource.DAV_RESOURCE_TYPE_REGULAR);
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
        setType(DAVResource.DAV_RESOURCE_TYPE_VERSION);
    }

    private void parseVersion(String parameter) throws SVNException {
        setVersioned(true);
        setType(DAVResource.DAV_RESOURCE_TYPE_VERSION);
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
            setType(DAVResource.DAV_RESOURCE_TYPE_PRIVATE);
            setExists(true);
            setVersioned(true);
            setBaseLined(true);
        } else {
            long revision = INVALID_REVISION;
            if (label != null) {
                try {
                    revision = Long.parseLong(label);
                } catch (NumberFormatException e) {
                    SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_ILLEGAL_URL, "Invalid label header ''{0}''", label));
                }
            }
            setType(DAVResource.DAV_RESOURCE_TYPE_VERSION);
            setRevision(revision);
            setVersioned(true);
            setBaseLined(true);
            setPath(null);
        }
    }

    public void prepare() throws SVNException {
        long latestRevision = getLatestRevision();
        if (getType() == DAVResource.DAV_RESOURCE_TYPE_REGULAR) {
            if (getRevision() == INVALID_REVISION) {
                setRevision(latestRevision);
            }
            checkPath();
            if (isCollection()) {
                getRepository().getDir(getPath(), getRevision(), getSVNProperties(), getEntries());
            } else {
                getRepository().getFile(getPath(), getRevision(), getSVNProperties(), null);
            }
        } else if (getType() == DAVResource.DAV_RESOURCE_TYPE_VERSION) {
            setRevision(latestRevision);
            setExists(true);
            // URI doesn't contain any information about context of requested uri
            setURI(DAVPathUtil.buildURI(null, DAVResourceKind.BASELINE, getRevision(), ""));
        } else if (getType() == DAVResource.DAV_RESOURCE_TYPE_WORKING) {
            //TODO: Define filename for ACTIVITY_ID under the repository
            if (isBaseLined()) {
                setExists(true);
                return;
            }
            checkPath();
        } else if (getType() == DAVResource.DAV_RESOURCE_TYPE_ACTIVITY) {
            //TODO: Define filename for ACTIVITY_ID under the repository
        }
    }

    private void checkPath() throws SVNException {
        SVNNodeKind currentNodeKind = getRepository().checkPath(getPath(), getRevision());
        setExists(currentNodeKind != SVNNodeKind.NONE && currentNodeKind != SVNNodeKind.UNKNOWN);
        setCollection(currentNodeKind == SVNNodeKind.DIR);
    }

    private boolean lacksETagPotential() {
        return (!exists() || (getType() != DAVResource.DAV_RESOURCE_TYPE_REGULAR && getType() != DAVResource.DAV_RESOURCE_TYPE_VERSION)
                || getType() == DAVResource.DAV_RESOURCE_TYPE_VERSION && isBaseLined());
    }

    public long getCreatedRevision() {
        String revisionParameter = getProperty(SVNProperty.COMMITTED_REVISION);
        try {
            return Long.parseLong(revisionParameter);
        } catch (NumberFormatException e) {
            return getRevision();
        }
    }

    public Date getLastModified() throws SVNException {
        if (lacksETagPotential()) {
            return null;
        }
        return getRevisionDate(getCreatedRevision());
    }


    public Date getRevisionDate(long revision) throws SVNException {
        return SVNTimeUtil.parseDate(getRevisionProperty(revision, SVNRevisionProperty.DATE));
    }

    public String getETag() {
        if (lacksETagPotential()) {
            return null;
        }
        StringBuffer eTag = new StringBuffer();
        eTag.append(isCollection() ? "W/" : "");
        eTag.append("\"");
        eTag.append(getCreatedRevision());
        eTag.append("/");
        eTag.append(SVNEncodingUtil.uriEncode(getPath()));
        eTag.append("\"");
        return eTag.toString();
    }

    public String getRepositoryUUID(boolean forceConnect) throws SVNException {
        return getRepository().getRepositoryUUID(forceConnect);
    }

    public String getContentType() throws SVNException {
        if (isBaseLined() && getType() == DAVResource.DAV_RESOURCE_TYPE_VERSION) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_PROPS_NOT_FOUND, "Failed to determine property"));
            return null;
        }
        if (getType() == DAVResource.DAV_RESOURCE_TYPE_PRIVATE && getKind() == DAVResourceKind.VCC) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_PROPS_NOT_FOUND, "Failed to determine property"));
            return null;
        }
        if (isCollection()) {
            return DEFAULT_COLLECTION_CONTENT_TYPE;
        }
        String contentType = getProperty(SVNProperty.MIME_TYPE);
        if (contentType != null) {
            return contentType;
        }
        return DEFAULT_FILE_CONTENT_TYPE;
    }

    public long getLatestRevision() throws SVNException {
        return getRepository().getLatestRevision();
    }

    public long getContentLength() throws SVNException {
        SVNDirEntry entry = getRepository().getDir(getPath(), getRevision(), false, null);
        return entry.getSize();
    }

    public String getAuthor(long revision) throws SVNException {
        return getRevisionProperty(revision, SVNRevisionProperty.AUTHOR);
    }

    public String getMD5Checksum() {
        return getProperty(SVNProperty.CHECKSUM);
    }

    public Collection getDeadProperties() {
        Collection deadProperties = new ArrayList();
        for (Iterator iterator = getSVNProperties().keySet().iterator(); iterator.hasNext();) {
            String propertyName = (String) iterator.next();
            if (SVNProperty.isRegularProperty(propertyName)) {
                deadProperties.add(propertyName);
            }
        }
        return deadProperties;
    }

    public String getLog(long revision) throws SVNException {
        return getRevisionProperty(revision, SVNRevisionProperty.LOG);
    }

    public String getProperty(String propertyName) {
        return (String) getSVNProperties().get(propertyName);
    }

    public String getRevisionProperty(long revision, String propertyName) throws SVNException {
        return getRepository().getRevisionPropertyValue(revision, propertyName);
    }

    public void output(OutputStream out) throws SVNException {
        if (isCollection()) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED));
        }
        getRepository().getFile(getPath(), getRevision(), null, out);
    }
}
