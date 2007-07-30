package org.tmatesoft.svn.core.internal.server.dav;

import org.tmatesoft.svn.core.internal.util.SVNPathUtil;

public class DAVResourceUtil {
    public static String SPECIAL_URI = "!svn";
    private static String DEDAULT_VCC_NAME = "default";

    public static String buildURI(String repositoryPath, DAVResourceKind davResourceKind, long revision, String uri, boolean addHrefTag) {
        String hrefOpen = addHrefTag ? "<D:href>" : "";
        String hrefClose = addHrefTag ? "</D:href>" : "";
        StringBuffer resultURI = new StringBuffer();
        resultURI.append(hrefOpen);
        resultURI.append(repositoryPath).append("/");
        if (davResourceKind == DAVResourceKind.ACT_COLLECTION) {
            resultURI.append(SPECIAL_URI).append("/");
            resultURI.append(davResourceKind.toString());
        } else if (davResourceKind == DAVResourceKind.BASELINE || davResourceKind == DAVResourceKind.BASELINE_COLL) {
            resultURI.append(SPECIAL_URI).append("/");
            resultURI.append(davResourceKind.toString()).append("/");
            resultURI.append(String.valueOf(revision));
        } else if (davResourceKind == DAVResourceKind.PUBLIC) {
            resultURI.append(uri).append("/");
        } else if (davResourceKind == DAVResourceKind.VERSION) {
            resultURI.append(SPECIAL_URI).append("/");
            resultURI.append(davResourceKind.toString()).append("/");
            resultURI.append(String.valueOf(revision));
            resultURI.append(uri).append("/");
        } else if (davResourceKind == DAVResourceKind.VCC) {
            resultURI.append(SPECIAL_URI).append("/");
            resultURI.append(davResourceKind.toString()).append("/");
            resultURI.append(DEDAULT_VCC_NAME);
        }
        resultURI.append(hrefClose);
        return resultURI.toString();
    }

    public static void parseURI(DAVResource resource, String label, boolean useCheckedIn) {
        String uri = resource.getURI();
        String repositoryName = SVNPathUtil.head(uri);
        resource.setRepositoryName(repositoryName);
        //TODO: create splitURI() method. URI with leading / doesn't work
        int specialURIIndex = uri.indexOf(SPECIAL_URI);
        int specialURILength = SPECIAL_URI.length();
        if (specialURIIndex == -1) {
            resource.setKind(DAVResourceKind.PUBLIC);
            resource.setType(DAVResource.DAV_RESOURCE_TYPE_REGULAR);
            resource.setPath(uri);
            resource.setParameterPath(uri);
        } else {
            String path = uri.substring(0, specialURIIndex);
            resource.setPath(path);
            String specialPart = uri.substring(specialURIIndex + specialURILength);
            if ("".equals(specialPart)) {
                // root/!svn
                resource.setType(DAVResource.DAV_RESOURCE_TYPE_PRIVATE);
                resource.setKind(DAVResourceKind.ROOT_COLLECTION);
            } else {
                if (specialPart.startsWith("/")) {
                    specialPart = specialPart.substring("/".length());
                }

                if (!specialPart.endsWith("/") && SVNPathUtil.getSegmentsCount(specialPart) <= 1) {
                    // root/!svn/XXX
                    resource.setType(DAVResource.DAV_RESOURCE_TYPE_PRIVATE);
                } else {
                    DAVResourceKind kind = DAVResourceKind.parseKind(SVNPathUtil.head(specialPart));
                    if (kind != DAVResourceKind.UNKNOWN) {
                        resource.setKind(kind);
                        String parameter = SVNPathUtil.removeHead(specialPart);
                        if (kind == DAVResourceKind.VCC) {
                            parseVCC(resource, parameter, label, useCheckedIn);
                        } else if (kind == DAVResourceKind.VERSION) {
                            parseVersion(resource, parameter);
                        } else if (kind == DAVResourceKind.BASELINE) {
                            parseBaseline(resource, parameter);
                        } else if (kind == DAVResourceKind.BASELINE_COLL) {
                            parseBaselineCollection(resource, parameter);
                        } else if (kind == DAVResourceKind.ACT_COLLECTION) {
                            parseActivity(resource, parameter);
                        } else if (kind == DAVResourceKind.HISTORY) {
                            parseHistory(resource, parameter);
                        } else if (kind == DAVResourceKind.WRK_BASELINE) {
                            parseWorkingBaseline(resource, parameter);
                        } else if (kind == DAVResourceKind.WORKING) {
                            parseWorking(resource, parameter);
                        }
                    }
                }
            }
        }
    }

    private static void parseWorking(DAVResource resource, String parameter) {
        resource.setType(DAVResource.DAV_RESOURCE_TYPE_WORKING);
        resource.setVersioned(true);
        resource.setWorking(true);
        int slashIndex = parameter.indexOf("/");
        if (slashIndex == 0) {
            //TODO: handle uri format mismatch
        }
        if (slashIndex == -1) {
            resource.setActivityID(parameter);
            resource.setParameterPath("/");
        } else {
            String activityID = parameter.substring(0, slashIndex);
            resource.setActivityID(activityID);
            resource.setParameterPath(parameter.substring(slashIndex));
        }
    }

    private static void parseWorkingBaseline(DAVResource resource, String parameter) {
        resource.setType(DAVResource.DAV_RESOURCE_TYPE_WORKING);
        resource.setWorking(true);
        resource.setVersioned(true);
        resource.setBaseLined(true);
        int slashIndex = parameter.indexOf("/");
//TODO: define correct conditions
        if (slashIndex == -1 || slashIndex == 0) {
            //TODO: handle uri format mismatch
        }
        String activityID = parameter.substring(0, slashIndex);
        resource.setActivityID(activityID);
        long revision = Long.parseLong(parameter.substring(slashIndex + "/".length()));
        resource.setRevision(revision);
    }

    private static void parseHistory(DAVResource resource, String parameter) {
        resource.setType(DAVResource.DAV_RESOURCE_TYPE_HISTORY);
        resource.setParameterPath(parameter);
    }

    private static void parseActivity(DAVResource resource, String parameter) {
        resource.setType(DAVResource.DAV_RESOURCE_TYPE_ACTIVITY);
        resource.setActivityID(parameter);
    }

    private static void parseBaselineCollection(DAVResource resource, String parameter) {
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
        resource.setType(DAVResource.DAV_RESOURCE_TYPE_REGULAR);
        resource.setVersioned(true);
        resource.setRevision(revision);
        resource.setParameterPath(parameterPath);

    }

    private static void parseBaseline(DAVResource resource, String parameter) {
        //TODO: handling number format exception
        resource.setRevision(Long.parseLong(parameter));
        resource.setVersioned(true);
        resource.setBaseLined(true);
        resource.setType(DAVResource.DAV_RESOURCE_TYPE_VERSION);

    }

    private static void parseVersion(DAVResource resource, String parameter) {
        resource.setVersioned(true);
        resource.setType(DAVResource.DAV_RESOURCE_TYPE_VERSION);
        int slashIndex = parameter.indexOf("/");
        if (slashIndex == -1) {
            resource.setRevision(Long.parseLong(parameter));
            resource.setParameterPath("/");
        } else if (slashIndex == 0) {
            //Requested URI ends with double slash
        } else {
            resource.setRevision(Long.parseLong(parameter.substring(0, slashIndex)));
            String parameterPath = parameter.substring(slashIndex);
            resource.setParameterPath(parameterPath);
        }
    }

    private static void parseVCC(DAVResource resource, String parameter, String label, boolean useCheckedIn) {
        if (!DEDAULT_VCC_NAME.equals(parameter)) {
            //Handle this
        }
        if (label == null && !useCheckedIn) {
            resource.setType(DAVResource.DAV_RESOURCE_TYPE_PRIVATE);
            resource.setExist(true);
            resource.setVersioned(true);
            resource.setBaseLined(true);

        } else {
            //TODO: choose INVALID_REVISION_NUMBER
            long revision = -1;
            if (label != null) {
                revision = Long.parseLong(label);
            }
            resource.setType(DAVResource.DAV_RESOURCE_TYPE_VERSION);
            resource.setRevision(revision);
            resource.setVersioned(true);
            resource.setBaseLined(true);
        }

    }

    public static void main(String[] args) {
        String uri = "repoview/bla/!svn/ver/0/tt";
        DAVResource resource = new DAVResource();
        resource.setURI(uri);
        parseURI(resource, "", false);
        System.out.println(" URI = " + resource.getURI() + "\n path = " + resource.getPath() + "\n kind = " + resource.getKind().toString() + "\n repository name = " + resource.getRepositoryName() + "\n revision = " + resource.getRevision() + "\n param path = " + resource.getParameterPath());
    }
}
