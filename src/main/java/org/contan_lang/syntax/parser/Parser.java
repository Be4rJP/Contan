package org.contan_lang.syntax.parser;

import org.contan_lang.ContanEngine;
import org.contan_lang.evaluators.*;
import org.contan_lang.operators.primitives.*;
import org.contan_lang.syntax.Identifier;
import org.contan_lang.syntax.Lexer;
import org.contan_lang.syntax.exception.ContanParseException;
import org.contan_lang.syntax.exception.UnexpectedSyntaxException;
import org.contan_lang.syntax.parser.environment.Scope;
import org.contan_lang.syntax.parser.environment.ScopeType;
import org.contan_lang.syntax.tokens.Token;
import org.contan_lang.variables.primitive.ContanFloat;
import org.contan_lang.variables.primitive.ContanInteger;
import org.contan_lang.variables.primitive.ContanString;
import org.contan_lang.variables.primitive.ContanVoid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Parser {

    private final ContanEngine contanEngine;

    public Parser(ContanEngine contanEngine) {
        this.contanEngine = contanEngine;
    }

    
    private List<PreLinkedFunctionEvaluator> preLinkedFunctions;
    private List<FunctionBlock> functions;

    private List<FunctionBlock> classFunctionBlocks;

    private List<Evaluator> classInitializers = new ArrayList<>();

    private List<PreLinkedCreateClassInstanceEvaluator> preLinkedCreateClassInstanceEvaluators;

    private Scope globalEnvironment;

    private String rootName;
    
    public synchronized ContanModule parse(String rootName, String text) throws ContanParseException {
        this.rootName = rootName;

        List<Token> tokens = Lexer.split(text);
        functions = new ArrayList<>();
        preLinkedFunctions = new ArrayList<>();
        classFunctionBlocks = new ArrayList<>();
        classInitializers = new ArrayList<>();
        globalEnvironment = new Scope(rootName, null, ScopeType.MODULE);

        preLinkedCreateClassInstanceEvaluators = new ArrayList<>();

        Evaluator globalEvaluator = parseBlock(globalEnvironment, tokens);
        
        for (PreLinkedFunctionEvaluator functionEvaluator : preLinkedFunctions) {
            functionEvaluator.link(functions);
        }

        for (PreLinkedCreateClassInstanceEvaluator classInstanceEvaluator : preLinkedCreateClassInstanceEvaluators) {
            contanEngine.linkClass(classInstanceEvaluator);
        }
        
        return new ContanModule(rootName, functions, globalEvaluator);
    }
    
    public Evaluator parseBlock(Scope environment, List<Token> tokens) throws ContanParseException {
        int length = tokens.size();
        
        List<Evaluator> blockEvaluators = new ArrayList<>();
        
        List<Token> expressionTokens = new ArrayList<>();
        for (int i = 0; i < length; i++) {
            Token token = tokens.get(i);
            
            Identifier identifier = null;
            if (token instanceof IdentifierToken) {
                identifier = ((IdentifierToken) token).getIdentifier();
            }

            if (identifier != null) {
                switch (identifier) {
                    case CLASS:

                    case IF: {
                        List<Token> first = getTokensUntilFindIdentifier(tokens, i, Identifier.BLOCK_START);
                        i += first.size();

                        List<Token> second = getNestedToken(tokens, i, Identifier.BLOCK_START, Identifier.BLOCK_END, true);
                        i += second.size();

                        first.addAll(second);
                        blockEvaluators.add(parseNestedBlock(environment, first));
                        continue;
                    }

                    case INITIALIZE: {
                        if (environment.getScopeType() != ScopeType.CLASS) {
                            throw new UnexpectedSyntaxException("");
                        }

                        List<Token> first = getTokensUntilFindIdentifier(tokens, i, Identifier.BLOCK_START);
                        i += first.size();

                        List<Token> second = getNestedToken(tokens, i, Identifier.BLOCK_START, Identifier.BLOCK_END, true);
                        i += second.size();

                        first.addAll(second);
                        parseNestedBlock(environment, first);
                        continue;
                    }

                    case FUNCTION: {
                        if (environment.getScopeType() == ScopeType.FUNCTION) {
                            throw new UnexpectedSyntaxException("");
                        }

                        List<Token> first = getTokensUntilFindIdentifier(tokens, i, Identifier.BLOCK_START);
                        i += first.size();

                        List<Token> second = getNestedToken(tokens, i, Identifier.BLOCK_START, Identifier.BLOCK_END, true);
                        i += second.size();

                        first.addAll(second);
                        blockEvaluators.add(parseNestedBlock(environment, first));
                        continue;
                    }

                    case BLOCK_START: {
                        List<Token> nestedToken = getNestedToken(tokens, i, Identifier.BLOCK_START, Identifier.BLOCK_END);
                        i += nestedToken.size();

                        blockEvaluators.add(parseBlock(environment, nestedToken));
                        continue;
                    }
                }
            }
            
            if (identifier != Identifier.EXPRESSION_SPLIT && identifier != Identifier.BLOCK_END) {
                expressionTokens.add(token);
            }
            
            if (identifier == Identifier.EXPRESSION_SPLIT || i == length - 1) {
                blockEvaluators.add(parseExpression(environment, expressionTokens));
                expressionTokens.clear();
            }
        }
        
        return new Expressions(blockEvaluators.toArray(new Evaluator[0]));
    }
    
    public Evaluator parseExpression(Scope environment, List<Token> tokens) throws ContanParseException {
        int length = tokens.size();
        
        
        //Remove block operator
        if (length >= 2) {
            Token ts = tokens.get(0);
            Token te = tokens.get(length - 1);
            
            if (ts instanceof IdentifierToken && te instanceof IdentifierToken) {
                if (((IdentifierToken) ts).getIdentifier() == Identifier.BLOCK_OPERATOR_START &&
                        ((IdentifierToken) te).getIdentifier() == Identifier.BLOCK_OPERATOR_END) {
                    return parseExpression(environment, getNestedToken(tokens, 0, Identifier.BLOCK_OPERATOR_START, Identifier.BLOCK_OPERATOR_END));
                }
            }
        }
        
        //Function
        if (tokens.size() >= 3) {
            if (tokens.get(0) instanceof NameToken) {
                if (tokens.get(1) instanceof IdentifierToken) {
                    Identifier identifier = ((IdentifierToken) tokens.get(1)).getIdentifier();
                    if (identifier == Identifier.BLOCK_OPERATOR_START) {
                        
                        List<Token> args = getNestedToken(tokens, 1, Identifier.BLOCK_OPERATOR_START, Identifier.BLOCK_OPERATOR_END);
                        
                        List<Token> evalTokens = new ArrayList<>();
                        List<Evaluator> evaluators = new ArrayList<>();
                        
                        int argLength = args.size();
                        for (int i = 0; i < argLength; i++) {
                            Token token = args.get(i);
                            
                            Identifier id = null;
                            if (token instanceof IdentifierToken) {
                                id = ((IdentifierToken) token).getIdentifier();
                            }

                            if (id == Identifier.BLOCK_OPERATOR_START) {
                                List<Token> inBlockTokens = getNestedToken(args, i, Identifier.BLOCK_OPERATOR_START, Identifier.BLOCK_OPERATOR_END, true);
                                i += inBlockTokens.size() - 1;
                                evalTokens.addAll(inBlockTokens);

                                if (i == argLength - 1) {
                                    evaluators.add(parseExpression(environment, evalTokens));
                                    evalTokens.clear();
                                    continue;
                                }
                            }
                            
                            if (id != Identifier.ARGUMENT_SPLIT) {
                                if (id != Identifier.BLOCK_OPERATOR_START) evalTokens.add(token);
                                if (i == argLength - 1) {
                                    evaluators.add(parseExpression(environment, evalTokens));
                                    evalTokens.clear();
                                }
                            } else {
                                evaluators.add(parseExpression(environment, evalTokens));
                                evalTokens.clear();
                            }
                        }
                        
                        if (argLength + 3 < tokens.size()) {
                            List<Token> nextTokens = tokens.subList(argLength + 3, tokens.size());
        
                            PreLinkedFunctionEvaluator functionEvaluator = new PreLinkedFunctionEvaluator(contanEngine, tokens.get(0), evaluators.toArray(new Evaluator[0]));
                            preLinkedFunctions.add(functionEvaluator);
        
                            Expression create = new Expression(new CreateVariableOperator(contanEngine, "data"));
                            Expression set = new Expression(new SetValueOperator(contanEngine, new NameToken("data"), functionEvaluator));
        
                            return new Expressions(create, set, parseExpression(environment, nextTokens));
                        }
                        
                        PreLinkedFunctionEvaluator functionEvaluator = new PreLinkedFunctionEvaluator(contanEngine, tokens.get(0), evaluators.toArray(new Evaluator[0]));
                        preLinkedFunctions.add(functionEvaluator);
                        return functionEvaluator;
                    }
                }
            }
        }
        
        
        int highestIdentifier = 0;
        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            
            if (token instanceof IdentifierToken) {
                Identifier identifier = ((IdentifierToken) token).getIdentifier();
                if (identifier == Identifier.BLOCK_OPERATOR_START) {
                    i += getNestedToken(tokens, i, Identifier.BLOCK_OPERATOR_START, Identifier.BLOCK_OPERATOR_END).size();
                }
                
                highestIdentifier = Math.max(highestIdentifier, identifier.priority);
            }
        }
        
        //String
        if (tokens.size() == 3) {
            Token t0 = tokens.get(0);
            Token t1 = tokens.get(1);
            Token t2 = tokens.get(2);
            
            if (t0 instanceof IdentifierToken && t2 instanceof IdentifierToken) {
                if (((IdentifierToken) t0).getIdentifier() == Identifier.DEFINE_STRING_START_OR_END
                        && ((IdentifierToken) t2).getIdentifier() == Identifier.DEFINE_STRING_START_OR_END) {
                    
                    char[] words = t1.getText().toCharArray();
                    StringBuilder stringBuilder = new StringBuilder();
                    
                    for (int i = 0; i < words.length; i++) {
                        char word = words[i];
                        
                        if (i <= words.length - 2 && word == '\\') {
                            if (words[i + 1] == '"') {
                                continue;
                            }
                        }
                        
                        stringBuilder.append(word);
                    }
                    
                    return new Expression(new DefinedValueOperator(contanEngine, new ContanString(stringBuilder.toString())));
                }
            }
        }
        
        //Number
        if (highestIdentifier == 0) {
            for (Token token : tokens) {
                if (token instanceof NameToken) {
                    String name = token.getText();
                    if (!name.matches("[+-]?\\d+(?:\\.\\d+)?")) {
                        environment.checkHasVariable(token);
                        return new Expression(new GetValueOperator(contanEngine, token));
                    } else if (name.contains(".")) {
                        return new Expression(new DefinedValueOperator(contanEngine, new ContanFloat(Double.parseDouble(name))));
                    } else {
                        return new Expression(new DefinedValueOperator(contanEngine, new ContanInteger(Long.parseLong(name))));
                    }
                }
            }
            return new Expression(new DefinedValueOperator(contanEngine, ContanVoid.INSTANCE));
        }
        
        //Parse expression
        List<Token> treeTokens = new ArrayList<>();
        List<Token> treeRTokens = new ArrayList<>();
        List<Token> treeLTokens = new ArrayList<>();
        Identifier id = null;
        int nest = 0;
        boolean isHitIdentifier = false;
        for (int i = length - 1; i >= 0; i--) {
            Token token = tokens.get(i);

            Identifier identifier = null;
            if (token instanceof IdentifierToken) {
                identifier = ((IdentifierToken) token).getIdentifier();
            }
    
    
            if (identifier == Identifier.BLOCK_OPERATOR_START) {
                nest--;
            }
            
            if (identifier != null) {
                
                if (identifier.priority >= Identifier.BLOCK_START.priority) {
                    throw new UnexpectedSyntaxException("" + identifier);
                }

                if (identifier.priority == highestIdentifier && nest == 0) {
                    if (!isHitIdentifier) {
                        Collections.reverse(treeTokens);
                        id = identifier;
                        treeRTokens = treeTokens;

                        treeTokens = new ArrayList<>();
                        isHitIdentifier = true;
                        continue;
                    }
                }
            }
    
            if (identifier == Identifier.BLOCK_OPERATOR_END) {
                nest++;
            }
            
            treeTokens.add(token);
            

            if (i == 0) {
                Collections.reverse(treeTokens);
                treeLTokens = treeTokens;
                break;
            }
        }
        
        
        if (id == null) {
            return new Expression(new DefinedValueOperator(contanEngine, ContanVoid.INSTANCE));
        }
        
        switch (id) {
            //data
            case DEFINE_VARIABLE: {
                if (treeRTokens.size() < 1) {
                    throw new UnexpectedSyntaxException("");
                }
                
                Token nameToken = treeRTokens.get(0);
                if (!(nameToken instanceof NameToken)) {
                    throw new UnexpectedSyntaxException("");
                }

                if (treeRTokens.size() > 2) {
                    
                    Token subsToken = treeRTokens.get(1);
                    if (!(subsToken instanceof IdentifierToken)) {
                        throw new UnexpectedSyntaxException("");
                    } else {
                        Identifier identifier = ((IdentifierToken) subsToken).getIdentifier();
                        if (identifier != Identifier.SUBSTITUTION) {
                            throw new UnexpectedSyntaxException("");
                        }
                    }

                    List<Token> setValueTokens = treeRTokens.subList(0, treeRTokens.size());
                    environment.addVariable(nameToken.getText());
                    Evaluator defineEval = new Expression(new CreateVariableOperator(contanEngine, nameToken.getText()));
                    Evaluator valueEval = parseExpression(environment, setValueTokens);
                    
                    return new Expressions(defineEval, valueEval);
                    
                } else {
                    return new Expression(new CreateVariableOperator(contanEngine, nameToken.getText()));
                }
            }

            //new
            case NEW: {
                if (treeRTokens.size() < 3) {
                    throw new UnexpectedSyntaxException("");
                }

                Token nameToken = treeRTokens.get(0);
                if (!(nameToken instanceof NameToken)) {
                    throw new UnexpectedSyntaxException("");
                }

                List<Token> args = getNestedToken(treeRTokens, 1, Identifier.BLOCK_OPERATOR_START, Identifier.BLOCK_OPERATOR_END);

                List<Token> evalTokens = new ArrayList<>();
                List<Evaluator> evaluators = new ArrayList<>();

                int argLength = args.size();
                for (int i = 0; i < argLength; i++) {
                    Token token = args.get(i);

                    Identifier identifier = null;
                    if (token instanceof IdentifierToken) {
                        identifier = ((IdentifierToken) token).getIdentifier();
                    }
    
                    if (identifier == Identifier.BLOCK_OPERATOR_START) {
                        List<Token> inBlockTokens = getNestedToken(args, i, Identifier.BLOCK_OPERATOR_START, Identifier.BLOCK_OPERATOR_END, true);
                        i += inBlockTokens.size() - 1;
                        evalTokens.addAll(inBlockTokens);
        
                        if (i == argLength - 1) {
                            evaluators.add(parseExpression(environment, evalTokens));
                            evalTokens.clear();
                            continue;
                        }
                    }

                    if (identifier != Identifier.ARGUMENT_SPLIT) {
                        if (identifier != Identifier.BLOCK_OPERATOR_START) evalTokens.add(token);
                        if (i == argLength - 1) {
                            evaluators.add(parseExpression(environment, evalTokens));
                            evalTokens.clear();
                        }
                    } else {
                        evaluators.add(parseExpression(environment, evalTokens));
                        evalTokens.clear();
                    }
                }

                PreLinkedCreateClassInstanceEvaluator classInstanceEvaluator;
                if (nameToken.getText().contains(".")) {
                    classInstanceEvaluator = new PreLinkedCreateClassInstanceEvaluator(nameToken.getText(), nameToken, evaluators.toArray(new Evaluator[0]));
                } else {
                    classInstanceEvaluator = new PreLinkedCreateClassInstanceEvaluator(null, nameToken, evaluators.toArray(new Evaluator[0]));
                }
                preLinkedCreateClassInstanceEvaluators.add(classInstanceEvaluator);

                if (args.size() + 3 < treeRTokens.size()) {
                    Evaluator createValue = new Expressions(new CreateVariableOperator(contanEngine, "data"));
                    Evaluator setValue = new Expression(new SetValueOperator(contanEngine, new NameToken("data"), classInstanceEvaluator));
                    Evaluator invoke = parseExpression(environment, treeRTokens.subList(args.size() + 3, treeRTokens.size()));
                    return new Expressions(createValue, setValue, invoke);
                }

                return classInstanceEvaluator;
            }
            
            case IMPORT: {
                if (treeRTokens.size() != 1) {
                    throw new UnexpectedSyntaxException("");
                }
                
                if (!(treeRTokens.get(0) instanceof NameToken)) {
                    throw new UnexpectedSyntaxException("");
                }
                
                try {
                    contanEngine.addJavaClass(treeRTokens.get(0).getText());
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new ContanParseException("");
                }
                
                return new Expressions();
            }

            //=
            case SUBSTITUTION: {
                if (treeRTokens.size() < 1 || treeLTokens.size() < 1 ) {
                    throw new UnexpectedSyntaxException("");
                }

                Token nameToken = treeLTokens.get(0);
                if (!(nameToken instanceof NameToken)) {
                    throw new UnexpectedSyntaxException("");
                }
                
                Evaluator valueEval = parseExpression(environment, treeRTokens);
                environment.checkHasVariable(nameToken);

                return new Expression(new SetValueOperator(contanEngine, nameToken, valueEval));
            }
            
            //+
            case OPERATOR_PLUS: {
                Evaluator left = parseExpression(environment, treeLTokens);
                Evaluator right = parseExpression(environment, treeRTokens);
                
                return new Expression(new AddOperator(contanEngine, left, right));
            }

            //*
            case OPERATOR_MULTIPLY: {
                Evaluator left = parseExpression(environment, treeLTokens);
                Evaluator right = parseExpression(environment, treeRTokens);
        
                return new Expression(new MultiplyOperator(contanEngine, left, right));
            }
            
            //==
            case OPERATOR_EQUAL: {
                Evaluator left = parseExpression(environment, treeLTokens);
                Evaluator right = parseExpression(environment, treeRTokens);
                
                return new Expression(new EqualOperator(contanEngine, left, right));
            }

            //return
            case RETURN: {
                return new Expression(new SetReturnValueOperator(contanEngine, parseExpression(environment, treeRTokens)));
            }
        }
        
        
        return new Expressions();
    }
    
    
    public Evaluator parseNestedBlock(Scope environment, List<Token> tokens) throws ContanParseException {
        if (tokens.size() == 0) return new Expressions();
        
        Token firstToken = tokens.get(0);
        Identifier firstIdentifier;
        if (firstToken instanceof IdentifierToken) {
            firstIdentifier = ((IdentifierToken) firstToken).getIdentifier();
        } else {
            throw new UnexpectedSyntaxException("");
        }

        switch (firstIdentifier) {
            case CLASS: {
                int length = tokens.size();
                if (length < 4) {
                    throw new UnexpectedSyntaxException("");
                }

                Token className = tokens.get(1);
                if (className instanceof IdentifierToken) {
                    throw new UnexpectedSyntaxException("");
                }

                if (className.getText().contains(".")) {
                    throw new UnexpectedSyntaxException("");
                }

                environment = new Scope(environment.getRootName() + "." + className.getText(), environment, ScopeType.CLASS);

                int index = 2;
                List<Token> argTokens = getTokensUntilFindIdentifier(tokens, index, Identifier.BLOCK_START);
                index += argTokens.size();

                List<Token> args = new ArrayList<>();
                for (Token t : argTokens) {
                    Identifier id = null;
                    if (t instanceof IdentifierToken) {
                        id = ((IdentifierToken) t).getIdentifier();
                    }

                    if (id != Identifier.ARGUMENT_SPLIT && id != Identifier.BLOCK_OPERATOR_START && id != Identifier.BLOCK_OPERATOR_END) {
                        args.add(t);
                        environment.addVariable(t.getText());
                    }
                }


                List<Token> blockTokens = getNestedToken(tokens, index, Identifier.BLOCK_START, Identifier.BLOCK_END);
                Evaluator blockEval = parseBlock(environment, blockTokens);


                ClassBlock classBlock = new ClassBlock(className, environment.getRootName(), args.toArray(new Token[0]));

                classFunctionBlocks.forEach(classBlock::addFunctionBlock);
                classInitializers.forEach(classBlock::addInitializer);
                classBlock.addInitializer(blockEval);
                contanEngine.addClassBlock(classBlock);
                classFunctionBlocks.clear();
                classInitializers.clear();

                return new Expressions();
            }

            case INITIALIZE: {
                int length = tokens.size();
                if (length < 3) {
                    throw new UnexpectedSyntaxException("");
                }

                Token secondToken = tokens.get(1);
                if (secondToken instanceof IdentifierToken) {
                    if (((IdentifierToken) secondToken).getIdentifier() != Identifier.BLOCK_START) {
                        throw new UnexpectedSyntaxException("");
                    }
                } else {
                    throw new UnexpectedSyntaxException("");
                }

                List<Token> blockTokens = getNestedToken(tokens, 1, Identifier.BLOCK_START, Identifier.BLOCK_END);
                classInitializers.add(parseBlock(environment, blockTokens));

                return new Expressions();
            }

            case FUNCTION: {
                int length = tokens.size();
                if (length < 6) {
                    throw new UnexpectedSyntaxException("");
                }

                Token functionName = tokens.get(1);
                if (functionName instanceof IdentifierToken) {
                    throw new UnexpectedSyntaxException("");
                }

                int index = 2;
                List<Token> argTokens = getTokensUntilFindIdentifier(tokens, index, Identifier.BLOCK_START);
                index += argTokens.size();

                List<Token> args = new ArrayList<>();
                for (Token t : argTokens) {
                    Identifier id = null;
                    if (t instanceof IdentifierToken) {
                        id = ((IdentifierToken) t).getIdentifier();
                    }

                    if (id != Identifier.ARGUMENT_SPLIT && id != Identifier.BLOCK_OPERATOR_START && id != Identifier.BLOCK_OPERATOR_END) {
                        args.add(t);
                        environment.addVariable(t.getText());
                    }
                }


                List<Token> blockTokens = getNestedToken(tokens, index, Identifier.BLOCK_START, Identifier.BLOCK_END);
                Evaluator blockEval = parseBlock(new Scope(environment.getRootName(), environment, ScopeType.FUNCTION), blockTokens);

                if (environment.getScopeType() == ScopeType.CLASS) {
                    classFunctionBlocks.add(new FunctionBlock(functionName, blockEval, args.toArray(new Token[0])));
                } else {
                    functions.add(new FunctionBlock(functionName, blockEval, args.toArray(new Token[0])));
                }
                return new Expressions();
            }

            case IF: {
                int length = tokens.size();
                if (length < 5) {
                    throw new UnexpectedSyntaxException("");
                }

                int index = 1;
                List<Token> ifTokens = getTokensUntilFindIdentifier(tokens, index, Identifier.BLOCK_START);
                index += ifTokens.size();

                List<Token> blockTokens = getNestedToken(tokens, index, Identifier.BLOCK_START, Identifier.BLOCK_END);

                Evaluator ifEval = parseExpression(environment, ifTokens);
                Evaluator blockEval = parseBlock(environment, blockTokens);

                return new IfEvaluator(ifEval, blockEval, null);
            }
        }
        
        throw new UnexpectedSyntaxException("");
    }


    public List<Token> getNestedToken(List<Token> tokens, int startIndex, Identifier start, Identifier end) throws ContanParseException {
        return getNestedToken(tokens, startIndex, start, end, false);
    }

    public List<Token> getNestedToken(List<Token> tokens, int startIndex, Identifier start, Identifier end, boolean contain) throws ContanParseException {
        int length = tokens.size();
        List<Token> nestedToken = new ArrayList<>();
        int nest = 0;
        for (int i = startIndex; i < length; i++) {
            Token token = tokens.get(i);
    
            Identifier identifier = null;
            if (token instanceof IdentifierToken) {
                identifier = ((IdentifierToken) token).getIdentifier();
            }
            
            if (identifier == end && i != startIndex) {
                nest--;

                if (contain && nest == 0) {
                    nestedToken.add(token);
                }

                if (nest == 0) {
                    return nestedToken;
                }
            }
            
            if (nest != 0 || contain) {
                nestedToken.add(token);
            }
            
            if (identifier == start) {
                nest++;
            }
            
        }
        
        throw new UnexpectedSyntaxException("");
    }


    public List<Token> getTokensUntilFindIdentifier(List<Token> tokens, int startIndex, Identifier end) throws ContanParseException {
        int length = tokens.size();
        List<Token> nestedToken = new ArrayList<>();
        for (int i = startIndex; i < length; i++) {
            Token token = tokens.get(i);

            Identifier identifier = null;
            if (token instanceof IdentifierToken) {
                identifier = ((IdentifierToken) token).getIdentifier();
            }

            if (identifier == end) {
                return nestedToken;
            }

            nestedToken.add(token);
        }

        throw new UnexpectedSyntaxException("");
    }
    
    
    
    public Identifier getNextIdentifier(List<Token> tokens, int index) {
        int length = tokens.size();
        for (int i = index; i < length; i++) {
            Token token = tokens.get(i);
        
            Identifier identifier = null;
            if (token instanceof IdentifierToken) {
                identifier = ((IdentifierToken) token).getIdentifier();
            }
        
            if (identifier != null) {
                return identifier;
            }
        }
        
        return null;
    }
    
}
