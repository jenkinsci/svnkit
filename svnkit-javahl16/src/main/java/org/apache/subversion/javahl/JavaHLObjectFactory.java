package org.apache.subversion.javahl;

import org.apache.subversion.javahl.types.NodeKind;

public class JavaHLObjectFactory {
    
    public static CommitItem createCommitItem(String p, NodeKind nk, int sf, String u, String cu, long r, String mf) {
        return new CommitItem(p, nk, sf, u, cu, r, mf);
    }
}
