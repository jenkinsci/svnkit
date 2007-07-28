package org.tmatesoft.svn.core.internal.server.dav;

import org.tmatesoft.svn.core.io.SVNRepository;

public class DAVResource {
    private String myURI;
    private String myRepositoryName;
    private SVNRepository myRepository;
    private String myUser;
    private DAVResourceKind myKind;
    private String myPath;
    private long myRevisionNumber;
    private String myParameterPath;
    private boolean myExist;
    private Boolean myIsCollection;
    private boolean myIsVersioned;
    private boolean myIsBaseLined;


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

    public long getRevisionNumber() {
        return myRevisionNumber;
    }

    public void setRevisionNumber(long revisionNumber) {
        myRevisionNumber = revisionNumber;
    }

    public String getParameterPath() {
        return myParameterPath;
    }

    public void setParameterPath(String parameterPath) {
        myParameterPath = parameterPath;
    }

    public boolean isExist() {
        return myExist;
    }

    public void setExist(boolean isExist) {
        myExist = isExist;
    }

    public Boolean isCollection() {
        return myIsCollection;
    }

    public void setCollection(Boolean isCollection) {
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
}
