package org.tmatesoft.svn.core.wc2;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc2.SvnGetInfoLocal;
import org.tmatesoft.svn.core.internal.wc2.SvnGetInfoRemote;

public class SvnOperationFactory {
    
    private Map<Class<?>, List<ISvnOperationRunner>> operationRunners;
    
    public SvnOperationFactory() {
        operationRunners = new HashMap<Class<?>, List<ISvnOperationRunner>>();
        
        registerOperationRunner(SvnGetInfo.class, new SvnGetInfoRemote());
        registerOperationRunner(SvnGetInfo.class, new SvnGetInfoLocal());
    }

    public ISvnOperationRunner getImplementation(SvnOperation operation) throws SVNException {
        if (operation == null) {
            return null;
        }
        List<ISvnOperationRunner> candidateRunners = operationRunners.get(operation.getClass());
        if (candidateRunners == null) {
            return null;
        }
        
        ISvnOperationRunner runner = null;
        for (ISvnOperationRunner candidateRunner : candidateRunners) {
            boolean isApplicable = candidateRunner.isApplicable(operation);
            if (!isApplicable) {
                continue;
            }
            runner = candidateRunner;
            break;
        }
        return runner;
    }
    
    protected void registerOperationRunner(Class<?> operationClass, ISvnOperationRunner runner) {
        if (operationClass == null || runner == null) {
            return;
        }
        List<ISvnOperationRunner> runners = operationRunners.get(operationClass);
        if (runners == null) {
            runners = new LinkedList<ISvnOperationRunner>();
            operationRunners.put(operationClass, runners);
        }
        runners.add(runner);
    }

}
