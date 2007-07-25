package org.tmatesoft.svn.core.internal.server.dav;

public class URIUtil {
    private static String specURI = "!svn";
    private static String defaultVCCName = "default";

    public static String buildURI(String repositoryPath, URIKind uriKind, long revision, String pathURI, boolean addHrefTag) {
        String hrefOpen = addHrefTag ? "<D:href>" : "";
        String hrefClose = addHrefTag ? "</D:href>" : "";
        StringBuffer resultURI = new StringBuffer();
        resultURI.append(hrefOpen);
        resultURI.append(repositoryPath).append("/");
        if (uriKind == URIKind.ACT_COLLECTION) {
            resultURI.append(specURI).append("/");
            resultURI.append(uriKind.toString());
        } else if (uriKind == URIKind.BASELINE || uriKind == URIKind.BASELINE_COLL) {
            resultURI.append(specURI).append("/");
            resultURI.append(uriKind.toString()).append("/");
            resultURI.append(String.valueOf(revision));
        } else if (uriKind == URIKind.PUBLIC) {
            resultURI.append(pathURI).append("/");
        } else if (uriKind == URIKind.VERSION) {
            resultURI.append(specURI).append("/");
            resultURI.append(uriKind.toString()).append("/");
            resultURI.append(String.valueOf(revision));
            resultURI.append(pathURI).append("/");
        } else if (uriKind == URIKind.VCC) {
            resultURI.append(specURI).append("/");
            resultURI.append(uriKind.toString()).append("/");
            resultURI.append(defaultVCCName);
        }
        resultURI.append(hrefClose);
        return resultURI.toString();
    }

    public static URI parseURI(String uri) {
        int specURIIndex = uri.indexOf(specURI);
        if (specURIIndex == -1) {
            return new URI(uri, URIKind.PUBLIC, "");
        } else {
            String path = uri.substring(0, specURIIndex);
            String specialPart = uri.substring(specURIIndex + ("/" + specURI + "/").length(), uri.length() - 1);
            URIKind kind = URIKind.parseKind(specialPart.substring(0, specialPart.indexOf("/")));
            String parameter = specialPart.substring(specialPart.indexOf("/"), specialPart.length() - 1);
            return new URI(path, kind, parameter);
        }
    }

}
