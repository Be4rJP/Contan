package org.contan_lang.operators.primitives;

import org.contan_lang.ContanEngine;
import org.contan_lang.environment.Environment;
import org.contan_lang.evaluators.Evaluator;
import org.contan_lang.operators.Operator;
import org.contan_lang.syntax.tokens.Token;
import org.contan_lang.variables.ContanVariable;
import org.contan_lang.variables.primitive.ContanVoid;

public class SetReturnValueOperator extends Operator {

    public SetReturnValueOperator(ContanEngine contanEngine, Token token, Evaluator... operators) {
        super(contanEngine, token, operators);
    }

    @Override
    public ContanVariable<?> eval(Environment environment) {
        environment.setReturnValue(operators[0].eval(environment));
        return ContanVoid.INSTANCE;
    }
}
