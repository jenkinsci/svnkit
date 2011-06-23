package org.tmatesoft.svn.core.internal.io.dav.http2;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolException;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.protocol.HttpContext;

public class HttpRedirectStrategy extends DefaultRedirectStrategy {

    @Override
    public HttpUriRequest getRedirect(HttpRequest request, HttpResponse response, HttpContext context) throws ProtocolException {
        if (request instanceof HttpDAVRequest) {
            HttpDAVRequest davRequest = (HttpDAVRequest) request;
            URI uri = getLocationURI(request, response, context);
            URI oldURI = davRequest.getURI();
            if (!oldURI.getPath().endsWith("/") && uri.getPath().endsWith("/")) {
                try {
                    oldURI = new URI(oldURI.getScheme(), oldURI.getUserInfo(), oldURI.getHost(), oldURI.getPort(), oldURI.getPath() + "/", oldURI.getQuery(), oldURI.getFragment());
                } catch (URISyntaxException e) {
                    throw new ProtocolException(e.getMessage());
                }
                if (oldURI.equals(uri)) {
                    davRequest = new HttpDAVRequest(davRequest.getMethod());
                    davRequest.setURI(uri);
                    return davRequest;
                }
            }
        }
        throw new ProtocolException(response.getStatusLine().toString());
    }

}
