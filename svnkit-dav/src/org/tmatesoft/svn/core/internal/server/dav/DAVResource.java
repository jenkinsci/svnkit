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
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.io.fs.FSRepository;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.io.SVNRepository;

/**
 * @author TMate Software Ltd.
 * @version 1.1.2
 */
public class DAVResource {

    private static final long INVALID_REVISION = FSRepository.INVALID_REVISION;

    public static final String SPECIAL_URI = "!svn";
    public static final String DEDAULT_VCC_NAME = "default";

    public static final int DAV_RESOURCE_TYPE_WORKING = -2;
    public static final int DAV_RESOURCE_TYPE_PRIVATE = 1;
    public static final int DAV_RESOURCE_TYPE_REGULAR = 0;
    public static final int DAV_RESOURCE_TYPE_VERSION = -1;
    public static final int DAV_RESOURCE_TYPE_ACTIVITY = 2;
    public static final int DAV_RESOURCE_TYPE_HISTORY = 3;

    private String myContext;
    private String myURI;
    private String myRepositoryName;
    private SVNRepository myRepository;
    private String myUser;
    private int myType;
    private DAVResourceKind myKind;
    private String myPath;
    private long myRevision;
    private long myCreatedRevision;
    private String myParameterPath;
    private String myActivityID;
    private boolean myIsExists;
    private boolean myIsCollection;
    private boolean myIsVersioned;
    private boolean myIsBaseLined;
    private boolean myIsWorking;

    public DAVResource(SVNRepository repository, String requestContext, String uri, String label, boolean useCheckedIn) throws SVNException {
        myRepository = repository;
        myContext = requestContext;
        myURI = uri;
        myRevision = INVALID_REVISION;
        //TODO: need to remember checksum if any
        //TODO: need to remembe User Agent if any
        parseURI(label, useCheckedIn);
        prepare();
    }

    public DAVResource(String path, DAVResourceKind kind, String parameter) {
        myPath = path;
        myKind = kind;
        myParameterPath = parameter;
        StringBuffer uri = new StringBuffer();
        uri.append(path).append("/");
        uri.append(kind.toString()).append("/");
        uri.append(parameter);
        myURI = uri.toString();
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

    public String getRepositoryName() {
        return myRepositoryName;
    }

    private void setRepositoryName(String repositoryName) {
        myRepositoryName = repositoryName;
    }

    public SVNRepository getRepository() {
        return myRepository;
    }

    public void setRepository(SVNRepository repository) {
        myRepository = repository;
    }

    public String getUser() {
        return myUser;
    }

    private void setUser(String user) {
        myUser = user;
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

    public String getPath() {
        return myPath;
    }

    private void setPath(String path) {
        myPath = path;
    }

    public long getRevision() {
        return myRevision;
    }

    private void setRevision(long revisionNumber) {
        myRevision = revisionNumber;
    }

    public long getCreatedRevision() {
        return myCreatedRevision;
    }

    public void setCreatedRevision(long createdRevision) {
        myCreatedRevision = createdRevision;
    }

    public String getParameterPath() {
        return myParameterPath;
    }

    private void setParameterPath(String parameterPath) {
        myParameterPath = parameterPath;
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

    public boolean lacksETagPotential() {
        return (!exists() || (getType() != DAVResource.DAV_RESOURCE_TYPE_REGULAR && getType() != DAVResource.DAV_RESOURCE_TYPE_VERSION) || getType() == DAVResource.DAV_RESOURCE_TYPE_VERSION && isBaseLined());
    }

    private void parseURI(String label, boolean useCheckedIn) {
        String uri = getURI();
        if (uri.startsWith("/")) {
            uri = uri.substring("/".length());
        }
        int specialURIIndex = uri.indexOf(SPECIAL_URI);
        int specialURILength = SPECIAL_URI.length();
        if (specialURIIndex == -1) {
            setKind(DAVResourceKind.PUBLIC);
            setType(DAVResource.DAV_RESOURCE_TYPE_REGULAR);
            setPath("".equals(uri) ? "/" : uri);
            setParameterPath(getPath());
            setVersioned(true);
        } else {
            String path = uri.substring(0, specialURIIndex);
            setPath("".equals(path) ? "/" : path);
            String specialPart = uri.substring(specialURIIndex + specialURILength);
            if ("".equals(specialPart)) {
                // root/!svn
                setType(DAVResource.DAV_RESOURCE_TYPE_PRIVATE);
                setKind(DAVResourceKind.ROOT_COLLECTION);
            } else {
                if (specialPart.startsWith("/")) {
                    specialPart = specialPart.substring("/".length());
                }

                if (!specialPart.endsWith("/") && SVNPathUtil.getSegmentsCount(specialPart) <= 1) {
                    // root/!svn/XXX
                    setType(DAVResource.DAV_RESOURCE_TYPE_PRIVATE);
                } else {
                    DAVResourceKind kind = DAVResourceKind.parseKind(SVNPathUtil.head(specialPart));
                    if (kind != DAVResourceKind.UNKNOWN) {
                        setKind(kind);
                        String parameter = SVNPathUtil.removeHead(specialPart);
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
        int slashIndex = parameter.indexOf("/");
        if (slashIndex == 0) {
            //TODO: handle uri format mismatch
        }
        if (slashIndex == -1) {
            setActivityID(parameter);
            setParameterPath("/");
        } else {
            setActivityID(parameter.substring(0, slashIndex));
            setParameterPath(parameter.substring(slashIndex));
        }
    }

    private void parseWorkingBaseline(String parameter) {
        setType(DAVResource.DAV_RESOURCE_TYPE_WORKING);
        setWorking(true);
        setVersioned(true);
        setBaseLined(true);
        int slashIndex = parameter.indexOf("/");
//TODO: define correct conditions
        if (slashIndex == -1 || slashIndex == 0) {
            //TODO: handle uri format mismatch
        }
        setActivityID(parameter.substring(0, slashIndex));
        long revision = Long.parseLong(parameter.substring(slashIndex + "/".length()));
        setRevision(revision);
    }

    private void parseHistory(String parameter) {
        setType(DAVResource.DAV_RESOURCE_TYPE_HISTORY);
        setParameterPath(parameter);
    }

    private void parseActivity(String parameter) {
        setType(DAVResource.DAV_RESOURCE_TYPE_ACTIVITY);
        setActivityID(parameter);
    }

    private void parseBaselineCollection(String parameter) {
        int slashIndex = parameter.indexOf("/");
        long revision = INVALID_REVISION;
        String parameterPath = null;
        if (slashIndex == -1) {
            parameterPath = "/";
            revision = Long.parseLong(parameter);
        } else if (slashIndex == 0) {
            //Revision number is missing
        } else {
            revision = Long.parseLong(parameter.substring(0, slashIndex));
            parameterPath = parameter.substring(slashIndex);
        }
        setType(DAVResource.DAV_RESOURCE_TYPE_REGULAR);
        setVersioned(true);
        setRevision(revision);
        setParameterPath(parameterPath);
    }

    private void parseBaseline(String parameter) {
        //TODO: handling number format exception
        setRevision(Long.parseLong(parameter));
        setVersioned(true);
        setBaseLined(true);
        setType(DAVResource.DAV_RESOURCE_TYPE_VERSION);

    }

    private void parseVersion(String parameter) {
        setVersioned(true);
        setType(DAVResource.DAV_RESOURCE_TYPE_VERSION);
        int slashIndex = parameter.indexOf("/");
        if (slashIndex == -1) {
            setRevision(Long.parseLong(parameter));
            setParameterPath("/");
        } else if (slashIndex == 0) {
            //Requested URI ends with double slash
        } else {
            setRevision(Long.parseLong(parameter.substring(0, slashIndex)));
            setParameterPath(parameter.substring(slashIndex));
        }
    }

    private void parseVCC(String parameter, String label, boolean useCheckedIn) {
        if (!DEDAULT_VCC_NAME.equals(parameter)) {
            //Handle this
        }
        if (label == null && !useCheckedIn) {
            setType(DAVResource.DAV_RESOURCE_TYPE_PRIVATE);
            setExists(true);
            setVersioned(true);
            setBaseLined(true);
        } else {
            long revision = INVALID_REVISION;
            if (label != null) {
                revision = Long.parseLong(label);
            }
            setType(DAVResource.DAV_RESOURCE_TYPE_VERSION);
            setRevision(revision);
            setVersioned(true);
            setBaseLined(true);
            setParameterPath(null);
        }

    }

    public void prepare() throws SVNException {
        long latestRevision = getRepository().getLatestRevision();
        if (getType() == DAVResource.DAV_RESOURCE_TYPE_REGULAR) {
            if (getRevision() == INVALID_REVISION) {
                setRevision(latestRevision);
            }
            SVNNodeKind currentNodeKind = null;
            try {
                getRepository().checkPath(getParameterPath(), getRevision());
            } catch (SVNException e) {
                if (e.getErrorMessage().getErrorCode() == SVNErrorCode.FS_NOT_DIRECTORY) {
                    currentNodeKind = SVNNodeKind.NONE;
                } else {
                    throw e;
                }
            }
            setExists(currentNodeKind != SVNNodeKind.NONE);
            setCollection(currentNodeKind == SVNNodeKind.DIR);
        } else if (getType() == DAVResource.DAV_RESOURCE_TYPE_VERSION) {
            setRevision(latestRevision);
            setExists(true);
            setURI(DAVResourceUtil.buildURI(getContext(), getPath(), DAVResourceKind.BASELINE, getRevision(), "", false));
        } else if (getType() == DAVResource.DAV_RESOURCE_TYPE_WORKING) {
            //TODO: Define filename for ACTIVITY_ID under the repository
            if (isBaseLined()) {
                setExists(true);
                return;
            }
            
            SVNNodeKind currentNodeKind = getRepository().checkPath(getParameterPath(), getRevision());
            setExists(currentNodeKind != SVNNodeKind.NONE);
            setCollection(currentNodeKind == SVNNodeKind.DIR);

        } else if (getType() == DAVResource.DAV_RESOURCE_TYPE_ACTIVITY) {
            //TODO: Define filename for ACTIVITY_ID under the repository
        }
    }

    public String getETag() {
        if (lacksETagPotential()) {
            return "";
        }
        StringBuffer eTag = new StringBuffer();
        eTag.append(isCollection() ? "W/" : "");
        eTag.append(getRevision());
        eTag.append("/");
        //TODO: replace '"' with &quote, < with &lt etc;
        eTag.append(getParameterPath());
        return eTag.toString();
    }
}
