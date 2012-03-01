package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.wc.ISVNAnnotateHandler;
import org.tmatesoft.svn.core.wc.SVNDiffOptions;
import org.tmatesoft.svn.core.wc.SVNRevision;

/**
 * Annotate operation. Obtains annotation information for each file text line from a repository
 * (using a working copy path to get a corresponding URL) and passes it to a
 * annotation handler if provided.
 * 
 * <p/>
 * {@link #run()} method returns first {@link SvnAnnotateItem} reported by the operation.
 * 
 * @author TMate Software Ltd.
 */
public class SvnAnnotate extends SvnReceivingOperation<SvnAnnotateItem> {
    
    private boolean useMergeHistory;
    private boolean ignoreMimeType;
    private ISVNAnnotateHandler handler;
    private SVNRevision startRevision;
    private SVNRevision endRevision;
    private String inputEncoding;
    private SVNDiffOptions diffOptions;
    
    protected SvnAnnotate(SvnOperationFactory factory) {
        super(factory);
    }
    
    /**
     * Gets the caller's handler to process annotation information.
     * 
     * @return handler to process annotation information if set
     */
    public ISVNAnnotateHandler getHandler() {
        return handler;
    }

    /**
     * Sets the caller's handler to process annotation information.
     * 
     * @param handler handler to process annotation information
     */
    public void setHandler(ISVNAnnotateHandler handler) {
        this.handler = handler;
    }

    /**
     * Gets whether or not data based upon revisions which have been merged to targets also should be returned.
     * 
     * @return <code>true</code> if merged history should be used, otherwise <code>false</code>
     */
    public boolean isUseMergeHistory() {
        return useMergeHistory;
    }

    /**
     * Sets whether or not data based upon revisions which have been merged to targets also should be returned.
     * 
     * @param useMergeHistory <code>true</code> if merged history should be use, otherwise <code>false</code>
     */
    public void setUseMergeHistory(boolean useMergeHistory) {
        this.useMergeHistory = useMergeHistory;
    }

    /**
     * Gets whether or not operation should be run on all files treated as text, 
     * no matter what SVNKit has inferred from the mime-type property.
     * 
     * @return <code>true</code> if mime types should be ignored, otherwise <code>false</code>
     */
    public boolean isIgnoreMimeType() {
        return ignoreMimeType;
    }

    /**
     * Sets whether or not operation should be run on all files treated as text, 
     * no matter what SVNKit has inferred from the mime-type property.
     * 
     * @param ignoreMimeType <code>true</code> if mime types should be ignored, otherwise <code>false</code>
     */
    public void setIgnoreMimeType(boolean ignoreMimeType) {
        this.ignoreMimeType = ignoreMimeType;
    }
    
    /**
     * Gets the revision of the operation to start from.
     * 
     * @return revision to start from
     */
    public SVNRevision getStartRevision() {
        return startRevision;
    }

    /**
     * Sets the revision of the operation to start from.
     * 
     * @param startRevision revision to start from
     */
    public void setStartRevision(SVNRevision startRevision) {
        this.startRevision = startRevision;
    }
    
    /**
     * Gets the revision of the operation to end with.
     * 
     * @return revision to end with
     */
    public SVNRevision getEndRevision() {
        return endRevision;
    }

    /**
     * Sets the revision of the operation to end with.
     * 
     * @param endRevision revision to end with
     */
    public void setEndRevision(SVNRevision endRevision) {
        this.endRevision = endRevision;
    }
    
    /**
     * Gets the name of character set to decode input bytes.
     * 
     * @return name of character set
     */
    public String getInputEncoding() {
        return inputEncoding;
    }

    /**
     * Sets the name of character set to decode input bytes.
     * 
     * @param inputEncoding name of character set
     */
    public void setInputEncoding(String inputEncoding) {
        this.inputEncoding = inputEncoding;
    }
    
    /**
     * Gets diff options for the operation.
     * 
     * @return diff options
     */
    public SVNDiffOptions getDiffOptions() {
        return diffOptions;
    }
    
    /**
     * Sets diff options for the operation.
     * 
     * @param diffOptions diff options
     */
    public void setDiffOptions(SVNDiffOptions diffOptions) {
    	this.diffOptions = diffOptions;
    }
    
    

}
