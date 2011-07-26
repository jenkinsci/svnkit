package org.tmatesoft.svn.core.wc2;

public abstract class SvnObject {
    
    private Object userData;

    public Object getUserData() {
        return userData;
    }

    public void setUserData(Object userData) {
        this.userData = userData;
    }
    
    
}
