package org.tmatesoft.svn.core.internal.server.dav;

public class DAVResource {
    private String myURI;
    private String myPath;
    private DAVResourceKind myDAVResourceKind;
    private String myParameter;

    public DAVResource(String path, DAVResourceKind kind, String parameter) {
        myPath = path;
        myDAVResourceKind = kind;
        myParameter = parameter;
        StringBuffer uri = new StringBuffer();
        uri.append(path).append("/");
        uri.append(kind.toString()).append("/");
        uri.append(parameter);
        myURI = uri.toString();
    }

    public String getURI() {
        return myURI;
    }

    public String getPath() {
        return myPath;
    }

    public DAVResourceKind getURIKind() {
        return myDAVResourceKind;
    }

    public String getParamter() {
        return myParameter;
    }

}
