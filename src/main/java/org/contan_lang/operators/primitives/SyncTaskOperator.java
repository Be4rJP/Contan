package org.contan_lang.operators.primitives;

import org.contan_lang.ContanEngine;
import org.contan_lang.environment.CoroutineStatus;
import org.contan_lang.environment.Environment;
import org.contan_lang.environment.expection.ContanRuntimeError;
import org.contan_lang.evaluators.Evaluator;
import org.contan_lang.runtime.ContanRuntimeUtil;
import org.contan_lang.syntax.tokens.Token;
import org.contan_lang.thread.ContanThread;
import org.contan_lang.variables.ContanObject;
import org.contan_lang.variables.primitive.ContanClassInstance;

public class SyncTaskOperator extends TaskOperator {

    public SyncTaskOperator(ContanEngine contanEngine, Token token, Evaluator... operators) {
        super(contanEngine, token, operators);
    }

    @Override
    public ContanObject<?> runTask(Environment environment) {
        CoroutineStatus coroutineStatus = environment.getCoroutineStatus(this);

        Environment newEnvironment;

        if (coroutineStatus == null) {
            ContanThread contanThread;
            ContanObject<?> thread = operators[0].eval(environment);
            thread = ContanRuntimeUtil.removeReference(token, thread);
            
            if (thread.getBasedJavaObject() instanceof ContanThread) {
                contanThread = (ContanThread) thread.getBasedJavaObject();
            } else {
                ContanRuntimeError.E0020.throwError("", null, token);
                return null;
            }
            
            newEnvironment = new Environment(contanEngine, environment, contanThread, operators[1], true);
            
            if (environment.getContanThread() == contanThread) {
                operators[1].eval(newEnvironment);
            } else {
                newEnvironment.rerun();
            }
            environment.setCoroutineStatus(this, 0, newEnvironment.getCompletable().getContanInstance());
        } else {
            return (ContanClassInstance) coroutineStatus.results[0];
        }

        return newEnvironment.getCompletable().getContanInstance();
    }

}
