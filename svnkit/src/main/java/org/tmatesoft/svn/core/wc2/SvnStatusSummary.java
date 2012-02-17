package org.tmatesoft.svn.core.wc2;

public class SvnStatusSummary extends SvnObject {

    private long minRevision;
    private long maxRevision;
    private boolean isModified;
    private boolean isSparseCheckout;
    private boolean isSwitched;
    
    public long getMinRevision() {
        return minRevision;
    }
    public long getMaxRevision() {
        return maxRevision;
    }
    public boolean isModified() {
        return isModified;
    }
    public boolean isSparseCheckout() {
        return isSparseCheckout;
    }
    public boolean isSwitched() {
        return isSwitched;
    }
    
    public void setMinRevision(long minRevision) {
        this.minRevision = minRevision;
    }
    public void setMaxRevision(long maxRevision) {
        this.maxRevision = maxRevision;
    }
    public void setModified(boolean isModified) {
        this.isModified = isModified;
    }
    public void setSparseCheckout(boolean isSparseCheckout) {
        this.isSparseCheckout = isSparseCheckout;
    }
    public void setSwitched(boolean isSwitched) {
        this.isSwitched = isSwitched;
    }
    
    public String toString() {
        StringBuffer result = new StringBuffer();
        result.append(getMinRevision());
        if (getMaxRevision() != getMinRevision()) {
            result.append(":");
            result.append(getMaxRevision());
        }
        
        result.append(isModified() ? "M" : "");
        result.append(isSwitched() ? "S" : "");
        result.append(isSparseCheckout() ? "P" : "");
        
        return result.toString();
    }
}
