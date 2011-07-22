package org.tmatesoft.svn.core.wc2;

public class SvnChecksum {
    
    public enum Kind { sha1, md5 }
    
    private Kind kind;
    private String digest;
    
    public SvnChecksum() {
        
    }
    
    public Kind getKind() {
        return kind;
    }
    public String getChecksum() {
        return digest;
    }
    public void setKind(Kind kind) {
        this.kind = kind;
    }
    public void setDigest(String digest) {
        this.digest = digest;
    }

}
