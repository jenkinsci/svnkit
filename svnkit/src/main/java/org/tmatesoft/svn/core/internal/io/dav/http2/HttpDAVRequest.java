package org.tmatesoft.svn.core.internal.io.dav.http2;

import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;

public class HttpDAVRequest extends HttpEntityEnclosingRequestBase {
    
    private String myMethod;

    public HttpDAVRequest(String methodName) {
        myMethod = methodName;
    }

    @Override
    public String getMethod() {
        return myMethod;
    }
}
