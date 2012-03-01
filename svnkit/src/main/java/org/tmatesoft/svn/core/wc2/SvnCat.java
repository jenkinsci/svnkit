package org.tmatesoft.svn.core.wc2;

import java.io.OutputStream;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc2.SvnWcGeneration;
import org.tmatesoft.svn.core.wc.SVNRevision;

/**
 * Operation for cat command. Outputs the content of file identified by target and 
 * revision to the output streams. 
 * 
 * <p/>
 * The actual node 
 * revision selected is determined by the target as it exists in 
 * {@link SvnTarget#getPegRevision()}. If target is remote and {@link SvnTarget#getPegRevision()} is 
 * {@link SVNRevision#UNDEFINED}, then it defaults to {@link SVNRevision#HEAD}. 
 * If target is local and {@link SvnTarget#getPegRevision()} is 
 * {@link SVNRevision#UNDEFINED}, then it defaults to {@link SVNRevision#WORKING}.
 * 
 * @author TMate Software Ltd.
 */
public class SvnCat extends SvnOperation<Void> {

    private boolean expandKeywords;
    private OutputStream output;

    protected SvnCat(SvnOperationFactory factory) {
        super(factory);
    }
    
    /**
     * Gets whether or not all keywords presenting in the file and listed in the file's
     * {@link org.tmatesoft.svn.core.SVNProperty#KEYWORDS}property (if set) should be substituted.
     * 
     * @return <code>true</code> if keywords should expanded, otherwise <code>false</code>
     */
    public boolean isExpandKeywords() {
        return expandKeywords;
    }

    /**
     * Sets whether or not all keywords presenting in the file and listed in the file's
     * {@link org.tmatesoft.svn.core.SVNProperty#KEYWORDS}property (if set) should be substituted.
     * 
     * @param <code>true</code> if keywords should expanded, otherwise <code>false</code>
     */
    public void setExpandKeywords(boolean expandKeywords) {
        this.expandKeywords = expandKeywords;
    }

    /**
     * Gets the output stream of the operation.
     * 
     * @return output stream
     */
    public OutputStream getOutput() {
        return output;
    }

    /**
     * Sets the output stream of the operation.
     * 
     * @param output output stream
     */
    public void setOutput(OutputStream output) {
        this.output = output;
    }

    @Override
    protected void ensureArgumentsAreValid() throws SVNException {
        super.ensureArgumentsAreValid();

        //here we assume we have one target

        SVNRevision resolvedPegRevision;
        SVNRevision resolvedRevision;

        if (getFirstTarget().getPegRevision() == SVNRevision.UNDEFINED) {
            resolvedPegRevision = getFirstTarget().getResolvedPegRevision(SVNRevision.HEAD, SVNRevision.WORKING);
            if (getRevision() == null || getRevision() == SVNRevision.UNDEFINED) {
                resolvedRevision = getFirstTarget().isURL() ? SVNRevision.HEAD : SVNRevision.BASE;
            } else {
                resolvedRevision = getRevision();
            }
        } else {
            resolvedPegRevision = getFirstTarget().getPegRevision();
            if (getRevision() == null || getRevision() == SVNRevision.UNDEFINED) {
                resolvedRevision = resolvedPegRevision;
            } else {
                resolvedRevision = getRevision();
            }
        }

        setRevision(resolvedRevision);
        setSingleTarget(
                getFirstTarget().isURL() ?
                        SvnTarget.fromURL(getFirstTarget().getURL(), resolvedPegRevision) :
                        SvnTarget.fromFile(getFirstTarget().getFile(), resolvedPegRevision));
    }
}
