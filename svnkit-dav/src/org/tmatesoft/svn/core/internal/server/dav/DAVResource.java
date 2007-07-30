package org.tmatesoft.svn.core.internal.server.dav;

import org.tmatesoft.svn.core.io.SVNRepository;

public class DAVResource {

    public static final int DAV_RESOURCE_TYPE_WORKING = -2;
    public static final int DAV_RESOURCE_TYPE_PRIVATE = 1;
    public static final int DAV_RESOURCE_TYPE_REGULAR = 0;
    public static final int DAV_RESOURCE_TYPE_VERSION = -1;
    public static final int DAV_RESOURCE_TYPE_ACTIVITY = 2;
    public static final int DAV_RESOURCE_TYPE_HISTORY = 3;

    private String myURI;
    private String myRepositoryName;
    private SVNRepository myRepository;
    private String myUser;
    private int myType;
    private DAVResourceKind myKind;
    private String myPath;
    private long myRevision;
    private String myParameterPath;
    private String myActivityID;
    private boolean myExist;
    private boolean myIsCollection;
    private boolean myIsVersioned;
    private boolean myIsBaseLined;
    private boolean myIsWorking;


    public DAVResource() {
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


    public String getURI() {
        return myURI;
    }

    public void setURI(String uri) {
        myURI = uri;
    }

    public String getRepositoryName() {
        return myRepositoryName;
    }

    public void setRepositoryName(String repositoryName) {
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

    public void setUser(String user) {
        myUser = user;
    }

    public int getType() {
        return myType;
    }

    public void setType(int type) {
        myType = type;
    }

    public DAVResourceKind getKind() {
        return myKind;
    }

    public void setKind(DAVResourceKind kind) {
        myKind = kind;
    }

    public String getPath() {
        return myPath;
    }

    public void setPath(String path) {
        myPath = path;
    }

    public long getRevision() {
        return myRevision;
    }

    public void setRevision(long revisionNumber) {
        myRevision = revisionNumber;
    }

    public String getParameterPath() {
        return myParameterPath;
    }

    public void setParameterPath(String parameterPath) {
        myParameterPath = parameterPath;
    }

    public String getActivityID() {
        return myActivityID;
    }

    public void setActivityID(String activityID) {
        myActivityID = activityID;
    }

    public boolean isExist() {
        return myExist;
    }

    public void setExist(boolean isExist) {
        myExist = isExist;
    }

    public boolean isCollection() {
        return myIsCollection;
    }

    public void setCollection(boolean isCollection) {
        myIsCollection = isCollection;
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
}
