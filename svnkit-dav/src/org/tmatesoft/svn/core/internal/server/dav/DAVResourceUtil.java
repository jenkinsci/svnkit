package org.tmatesoft.svn.core.internal.server.dav;

public class DAVResourceUtil {
    private static String specURI = "!svn";
    private static String defaultVCCName = "default";

    public static String buildURI(String repositoryPath, DAVResourceKind davResourceKind, long revision, String uri, boolean addHrefTag) {
        String hrefOpen = addHrefTag ? "<D:href>" : "";
        String hrefClose = addHrefTag ? "</D:href>" : "";
        StringBuffer resultURI = new StringBuffer();
        resultURI.append(hrefOpen);
        resultURI.append(repositoryPath).append("/");
        if (davResourceKind == DAVResourceKind.ACT_COLLECTION) {
            resultURI.append(specURI).append("/");
            resultURI.append(davResourceKind.toString());
        } else if (davResourceKind == DAVResourceKind.BASELINE || davResourceKind == DAVResourceKind.BASELINE_COLL) {
            resultURI.append(specURI).append("/");
            resultURI.append(davResourceKind.toString()).append("/");
            resultURI.append(String.valueOf(revision));
        } else if (davResourceKind == DAVResourceKind.PUBLIC) {
            resultURI.append(uri).append("/");
        } else if (davResourceKind == DAVResourceKind.VERSION) {
            resultURI.append(specURI).append("/");
            resultURI.append(davResourceKind.toString()).append("/");
            resultURI.append(String.valueOf(revision));
            resultURI.append(uri).append("/");
        } else if (davResourceKind == DAVResourceKind.VCC) {
            resultURI.append(specURI).append("/");
            resultURI.append(davResourceKind.toString()).append("/");
            resultURI.append(defaultVCCName);
        }
        resultURI.append(hrefClose);
        return resultURI.toString();
    }

    public static DAVResource parseURI(String uri) {
        int specURIIndex = uri.indexOf(specURI);
        if (specURIIndex == -1) {
            return new DAVResource(uri, DAVResourceKind.PUBLIC, "");
        } else {
            String path = uri.substring(0, specURIIndex);
            String specialPart = uri.substring(specURIIndex + ("/" + specURI + "/").length(), uri.length() - 1);
            DAVResourceKind kind = DAVResourceKind.parseKind(specialPart.substring(0, specialPart.indexOf("/")));
            String parameter = specialPart.substring(specialPart.indexOf("/"), specialPart.length() - 1);
            return new DAVResource(path, kind, parameter);
        }
    }

}
