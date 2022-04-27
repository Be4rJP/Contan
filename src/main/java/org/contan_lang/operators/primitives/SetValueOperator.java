package org.contan_lang.operators.primitives;

import org.contan_lang.ContanEngine;
import org.contan_lang.environment.ContanObjectReference;
import org.contan_lang.environment.Environment;
import org.contan_lang.environment.expection.ContanRuntimeError;
import org.contan_lang.evaluators.Evaluator;
import org.contan_lang.operators.Operator;
import org.contan_lang.syntax.tokens.Token;
import org.contan_lang.variables.ContanObject;
import org.contan_lang.variables.primitive.ContanNull;

public class SetValueOperator extends Operator {
    
    public SetValueOperator(ContanEngine contanEngine, Token token, Evaluator... operators) {
        super(contanEngine, token, operators);
    }
    
    @Override
    public ContanObject<?> eval(Environment environment) {
        ContanObject<?> variable = operators[0].eval(environment);
        
        if (!(variable instanceof ContanObjectReference)) {
            ContanRuntimeError.E0003.throwError("", null, token);
            return null;
        }
        
        try {
            ContanObject<?> rightResult = operators[1].eval(environment);
            if (rightResult instanceof ContanObjectReference) {
                rightResult = ((ContanObjectReference) rightResult).getContanVariable();
            }

            ContanObject<?> resultClone = rightResult.createClone();
            ((ContanObjectReference) variable).setContanVariable(resultClone);
            
            return resultClone;
        } catch (IllegalAccessException e) {
            ContanRuntimeError.E0012.throwError("", e, token);
        }
        
        return ContanNull.INSTANCE;
    }
}
