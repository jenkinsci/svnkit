package org.tmatesoft.svn.core.internal.server.dav;

public class URI {
    private String myURI;
    private String myPath;
    private URIKind myURIKind;
    private String myParameter;

    public URI(String path, URIKind kind, String parameter) {
        myPath = path;
        myURIKind = kind;
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

    public URIKind getURIKind() {
        return myURIKind;
    }

    public String getParamter() {
        return myParameter;
    }

}
