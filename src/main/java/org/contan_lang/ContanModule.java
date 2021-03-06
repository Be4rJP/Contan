package org.contan_lang;

import org.contan_lang.environment.Environment;
import org.contan_lang.environment.expection.ContanJavaRuntimeException;
import org.contan_lang.environment.expection.ContanRuntimeError;
import org.contan_lang.evaluators.ClassBlock;
import org.contan_lang.evaluators.Evaluator;
import org.contan_lang.evaluators.FunctionBlock;
import org.contan_lang.evaluators.FunctionInvokable;
import org.contan_lang.syntax.tokens.Token;
import org.contan_lang.thread.ContanThread;
import org.contan_lang.variables.ContanObject;
import org.contan_lang.variables.primitive.ContanClassObject;
import org.contan_lang.variables.primitive.ContanVoidObject;
import org.contan_lang.variables.primitive.JavaClassInstance;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;

public class ContanModule implements FunctionInvokable {
    
    private final ContanEngine contanEngine;
    
    private final String rootName;
    
    private final Map<String, List<FunctionBlock>> functionMap;
    
    private final List<FunctionBlock> functionBlocks;

    private final Map<String, ClassBlock> classBlockMap;

    private final List<ClassBlock> classBlocks;

    private final Evaluator globalEvaluator;

    private final Environment moduleEnvironment;
    
    public ContanModule(ContanEngine contanEngine, String rootName, List<FunctionBlock> functionBlocks, List<ClassBlock> classBlocks, Evaluator globalEvaluator, Environment moduleEnvironment) {
        this.contanEngine = contanEngine;
        this.rootName = rootName;
        this.functionBlocks = functionBlocks;
        this.classBlocks = classBlocks;
        this.globalEvaluator = globalEvaluator;
        this.functionMap = new HashMap<>();
        this.classBlockMap = new HashMap<>();
        this.moduleEnvironment = moduleEnvironment;
        
        for (FunctionBlock functionBlock : functionBlocks) {
            List<FunctionBlock> functions = functionMap.computeIfAbsent(functionBlock.getFunctionName().getText(), k -> new ArrayList<>());
            functions.add(functionBlock);
        }

        for (ClassBlock classBlock : classBlocks) {
            classBlockMap.put(classBlock.getClassName().getText(), classBlock);
            moduleEnvironment.createVariable(classBlock.getClassName().getText(), new ContanClassObject(contanEngine, classBlock));
        }
    }

    public String getRootName() {return rootName;}

    public List<FunctionBlock> getFunctionBlocks() {return functionBlocks;}

    public Evaluator getGlobalEvaluator() {return globalEvaluator;}

    public Environment getModuleEnvironment() {return moduleEnvironment;}
    
    public @Nullable ClassBlock getClassByName(String className) {
        for (ClassBlock classBlock : classBlocks) {
            if (classBlock.getClassName().getText().equals(className)) {
                return classBlock;
            }
        }
        
        return null;
    }
    
    public List<ClassBlock> getClassBlocks() {return classBlocks;}
    
    private boolean initialized = false;
    
    private final ReentrantLock INITIALIZE_LOCK = new ReentrantLock(true);
    
    public void initialize(ContanThread currentThread) throws Exception {
        try {
            INITIALIZE_LOCK.lock();
            
            if (initialized) {
                return;
            }
            initialized = true;
        } finally {
            INITIALIZE_LOCK.unlock();
        }


        Callable<?> initializeTask = () -> {
            ContanObject<?> result = globalEvaluator.eval(moduleEnvironment);

            //Eval class extends
            for (ClassBlock classBlock : classBlocks) {
                classBlock.initializeClassInfo(moduleEnvironment);
            }

            if (moduleEnvironment.hasReturnValue()) {
                return moduleEnvironment.getReturnValue();
            } else {
                return result;
            }
        };

        if (currentThread == contanEngine.getMainThread()) {
            initializeTask.call();
        } else {
            contanEngine.getMainThread().runTaskImmediately(initializeTask);
        }
    }

    @Override
    public ContanObject<?> invokeFunction(ContanThread contanThread, Token functionName, ContanObject<?>... variables) {
        List<FunctionBlock> functions = functionMap.get(functionName.getText());
        if (functions != null) {
            for (FunctionBlock functionBlock : functions) {
                if (functionBlock.getArgs().length == variables.length) {
                    return functionBlock.eval(new Environment(contanEngine, moduleEnvironment, contanThread), null, contanThread, variables);
                }
            }
        }
    
        ContanRuntimeError.E0011.throwError("", null, functionName);
        return ContanVoidObject.INSTANCE;
    }
    
    
    public Object invokeFunctionSync(ContanThread contanThread, String functionName, Object... arguments) throws ExecutionException, InterruptedException {
        //Convert all arguments to ContanObject.
        ContanObject<?>[] variables = new ContanObject<?>[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            variables[i] = new JavaClassInstance(contanEngine, arguments[i]);
        }
        
        //Invoke function
        List<FunctionBlock> functions = functionMap.get(functionName);
        if (functions != null) {
            for (FunctionBlock functionBlock : functions) {
                if (functionBlock.getArgs().length == variables.length) {
                    ContanObject<?> result = contanThread.runTaskImmediately(() ->
                        functionBlock.eval(new Environment(contanEngine, moduleEnvironment, contanThread), null, contanThread, variables)
                    );
                    if (result instanceof ContanVoidObject) {
                        return null;
                    } else {
                        return result.convertToJavaObject();
                    }
                }
            }
        }
        
        throw new ContanJavaRuntimeException("Function not found : " + functionName, null);
    }
    
    
    public Object invokeFunction(ContanThread contanThread, String functionName, Object... arguments) throws ExecutionException, InterruptedException {
        //Convert all arguments to ContanObject.
        ContanObject<?>[] variables = new ContanObject<?>[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            variables[i] = new JavaClassInstance(contanEngine, arguments[i]);
        }
        
        //Invoke function
        List<FunctionBlock> functions = functionMap.get(functionName);
        if (functions != null) {
            for (FunctionBlock functionBlock : functions) {
                if (functionBlock.getArgs().length == variables.length) {
                    ContanObject<?> result = functionBlock.eval(new Environment(contanEngine, moduleEnvironment, contanThread), null, contanThread, variables);
                    
                    if (result instanceof ContanVoidObject) {
                        return null;
                    } else {
                        return result.convertToJavaObject();
                    }
                }
            }
        }
        
        throw new ContanJavaRuntimeException("Function not found : " + functionName, null);
    }


    public void invokeFunctionAsync(ContanThread contanThread, String functionName, Object... arguments) throws ExecutionException, InterruptedException {
        //Convert all arguments to ContanObject.
        ContanObject<?>[] variables = new ContanObject<?>[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            variables[i] = new JavaClassInstance(contanEngine, arguments[i]);
        }

        //Invoke function
        List<FunctionBlock> functions = functionMap.get(functionName);
        if (functions != null) {
            for (FunctionBlock functionBlock : functions) {
                if (functionBlock.getArgs().length == variables.length) {
                    contanThread.scheduleTask(() ->
                            functionBlock.eval(new Environment(contanEngine, moduleEnvironment, contanThread), null, contanThread, variables)
                    );
                    return;
                }
            }
        }

        throw new ContanJavaRuntimeException("Function not found : " + functionName, null);
    }
    
}
