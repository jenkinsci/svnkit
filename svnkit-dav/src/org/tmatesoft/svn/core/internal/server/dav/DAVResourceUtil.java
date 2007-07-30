package org.tmatesoft.svn.core.internal.server.dav;


public class DAVResourceUtil {
    
    private static String DEDAULT_VCC_NAME = "default";

    public static String buildURI(String repositoryPath, DAVResourceKind davResourceKind, long revision, String uri, boolean addHrefTag) {
        String hrefOpen = addHrefTag ? "<D:href>" : "";
        String hrefClose = addHrefTag ? "</D:href>" : "";
        StringBuffer resultURI = new StringBuffer();
        resultURI.append(hrefOpen);
        resultURI.append(repositoryPath).append("/");
        if (davResourceKind == DAVResourceKind.ACT_COLLECTION) {
            resultURI.append(DAVResource.SPECIAL_URI).append("/");
            resultURI.append(davResourceKind.toString());
        } else if (davResourceKind == DAVResourceKind.BASELINE || davResourceKind == DAVResourceKind.BASELINE_COLL) {
            resultURI.append(DAVResource.SPECIAL_URI).append("/");
            resultURI.append(davResourceKind.toString()).append("/");
            resultURI.append(String.valueOf(revision));
        } else if (davResourceKind == DAVResourceKind.PUBLIC) {
            resultURI.append(uri).append("/");
        } else if (davResourceKind == DAVResourceKind.VERSION) {
            resultURI.append(DAVResource.SPECIAL_URI).append("/");
            resultURI.append(davResourceKind.toString()).append("/");
            resultURI.append(String.valueOf(revision));
            resultURI.append(uri).append("/");
        } else if (davResourceKind == DAVResourceKind.VCC) {
            resultURI.append(DAVResource.SPECIAL_URI).append("/");
            resultURI.append(davResourceKind.toString()).append("/");
            resultURI.append(DEDAULT_VCC_NAME);
        }
        resultURI.append(hrefClose);
        return resultURI.toString();
    }
}
