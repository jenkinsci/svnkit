package org.tmatesoft.svn.core.wc2;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.util.SVNLogType;

public class SvnOperationFactory {
    
    private List<Map<Class<?>, ISvnOperationRunner>> operationRunners;
    
    public SvnOperationFactory() {
        operationRunners = new LinkedList<Map<Class<?>, ISvnOperationRunner>>();
        
        registerOperationRunners();
    }

    public ISvnOperationRunner getImplementation(SvnOperation operation) throws SVNException {
        List<ISvnOperationRunner> candidateRunners = new LinkedList<ISvnOperationRunner>();
        for (Map<Class<?>, ISvnOperationRunner> runners : operationRunners) {
            ISvnOperationRunner candidateRunner = runners.get(operation.getClass());
            if (candidateRunner != null) {
                candidateRunners.add(candidateRunner);
            }
        }
        
        SVNErrorMessage lastError = null;
        ISvnOperationRunner runner = null;
        for (ISvnOperationRunner candidateRunner : candidateRunners) {
            try {
                boolean isApplicable = candidateRunner.isApplicable(operation);
                if (!isApplicable) {
                    continue;
                }
                runner = candidateRunner;
                break;
            } catch (SVNException e) {
                lastError = e.getErrorMessage();
            }
        }
        
        if (runner != null) {
            runner.run(operation);
        }
        if (lastError != null) {
            SVNErrorManager.error(lastError, SVNLogType.WC);
        }
        return null;
    }
    
    protected void registerOperationRunners() {
    }

}
