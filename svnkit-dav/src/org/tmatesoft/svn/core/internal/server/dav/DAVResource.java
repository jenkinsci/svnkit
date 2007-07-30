package org.tmatesoft.svn.core.internal.server.dav;

import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.io.SVNRepository;

public class DAVResource {

    public static final String SPECIAL_URI = "!svn";
    public static final String DEDAULT_VCC_NAME = "default";

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
    private boolean myIsExists;
    private boolean myIsCollection;
    private boolean myIsVersioned;
    private boolean myIsBaseLined;
    private boolean myIsWorking;


    public DAVResource(String uri, String label, boolean useCheckedIn) {
        myURI = uri;
        parseURI(label, useCheckedIn);
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

    public boolean exists() {
        return myIsExists;
    }

    public void setExists(boolean isExist) {
        myIsExists = isExist;
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
    
    private void parseURI(String label, boolean useCheckedIn) {
        String uri = getURI();
        String repositoryName = SVNPathUtil.head(uri);
        setRepositoryName(repositoryName);
        //TODO: create splitURI() method. URI with leading / doesn't work
        int specialURIIndex = uri.indexOf(SPECIAL_URI);
        int specialURILength = SPECIAL_URI.length();
        if (specialURIIndex == -1) {
            setKind(DAVResourceKind.PUBLIC);
            setType(DAVResource.DAV_RESOURCE_TYPE_REGULAR);
            setPath(uri);
            setParameterPath(uri);
        } else {
            String path = uri.substring(0, specialURIIndex);
            setPath(path);
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
            String activityID = parameter.substring(0, slashIndex);
            setActivityID(activityID);
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
        String activityID = parameter.substring(0, slashIndex);
        setActivityID(activityID);
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
        long revision = -1;
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
            String parameterPath = parameter.substring(slashIndex);
            setParameterPath(parameterPath);
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
            //TODO: choose INVALID_REVISION_NUMBER
            long revision = -1;
            if (label != null) {
                revision = Long.parseLong(label);
            }
            setType(DAVResource.DAV_RESOURCE_TYPE_VERSION);
            setRevision(revision);
            setVersioned(true);
            setBaseLined(true);
        }

    }

    public static void main(String[] args) {
        String uri = "repoview/bla/!svn/ver/0/tt";
        DAVResource resource = new DAVResource(uri, null, false);
        System.out.println(" URI = " + resource.getURI() + "\n path = " + resource.getPath() + "\n kind = " + resource.getKind().toString() + "\n repository name = " + resource.getRepositoryName() + "\n revision = " + resource.getRevision() + "\n param path = " + resource.getParameterPath());
    }

}
