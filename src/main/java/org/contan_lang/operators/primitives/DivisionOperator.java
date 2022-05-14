package org.contan_lang.operators.primitives;

import org.contan_lang.ContanEngine;
import org.contan_lang.environment.CoroutineStatus;
import org.contan_lang.environment.Environment;
import org.contan_lang.environment.expection.ContanRuntimeError;
import org.contan_lang.evaluators.Evaluator;
import org.contan_lang.operators.Operator;
import org.contan_lang.runtime.ContanRuntimeUtil;
import org.contan_lang.syntax.tokens.Token;
import org.contan_lang.variables.ContanObject;
import org.contan_lang.variables.primitive.ContanF64;
import org.contan_lang.variables.primitive.ContanI64;
import org.contan_lang.variables.primitive.ContanYieldObject;

public class DivisionOperator extends Operator {

    public DivisionOperator(ContanEngine contanEngine, Token token, Evaluator... operators) {
        super(contanEngine, token, operators);
    }

    @Override
    public ContanObject<?> eval(Environment environment) {
        CoroutineStatus coroutineStatus = environment.getCoroutineStatus(this);
        ContanObject<?> contanObject0;
        ContanObject<?> contanObject1;

        if (coroutineStatus == null) {
            contanObject0 = operators[0].eval(environment);
            if (environment.hasYieldReturnValue() || contanObject0 == ContanYieldObject.INSTANCE) {
                environment.setCoroutineStatus(this, 0, ContanYieldObject.INSTANCE);
                environment.setReturnValue(ContanYieldObject.INSTANCE);
                return ContanYieldObject.INSTANCE;
            }

            contanObject1 = operators[1].eval(environment);
            if (environment.hasYieldReturnValue() || contanObject1 == ContanYieldObject.INSTANCE) {
                environment.setCoroutineStatus(this, 1, contanObject0);
                environment.setReturnValue(ContanYieldObject.INSTANCE);
                return ContanYieldObject.INSTANCE;
            }
        } else {
            if (coroutineStatus.count == 0) {
                contanObject0 = operators[0].eval(environment);
            } else {
                contanObject0 = coroutineStatus.results[0];
            }
            contanObject1 = operators[1].eval(environment);
        }
    
        contanObject0 = ContanRuntimeUtil.removeReference(token, contanObject0);
        contanObject1 = ContanRuntimeUtil.removeReference(token, contanObject1);
        
        Object first = contanObject0.getBasedJavaObject();
        Object second = contanObject1.getBasedJavaObject();

        if ((first instanceof Integer || first instanceof Long || first instanceof Float || first instanceof Double) &&
                (second instanceof Integer || second instanceof Long || second instanceof Float || second instanceof Double)) {

            if (first instanceof Float || first instanceof Double || second instanceof Float || second instanceof Double) {
                double sum;

                if (first instanceof Integer) {
                    sum = (Integer) first;
                } else if (first instanceof Long) {
                    sum = (Long) first;
                } else if (first instanceof Float) {
                    sum = (Float) first;
                } else {
                    sum = (Double) first;
                }

                if (second instanceof Integer) {
                    sum /= (Integer) second;
                } else if (second instanceof Long) {
                    sum /= (Long) second;
                } else if (second instanceof Float) {
                    sum /= (Float) second;
                } else {
                    sum /= (Double) second;
                }

                return new ContanF64(contanEngine, sum);
            }


            long sum;

            if (first instanceof Integer) {
                sum = (Integer) first;
            } else {
                sum = (Long) first;
            }

            if (second instanceof Integer) {
                sum /= (Integer) second;
            } else {
                sum /= (Long) second;
            }

            return new ContanI64(contanEngine, sum);
        }

        ContanRuntimeError.E0002.throwError(System.lineSeparator() + "Left : " + first.toString()
                + System.lineSeparator() + "Right : " + second.toString(), null, token);
        return null;
    }

}