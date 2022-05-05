package org.contan_lang.thread;

import org.contan_lang.ContanEngine;

import java.util.concurrent.*;

public class BasicContanThread implements ContanThread {
    
    private final ContanEngine contanEngine;
    
    private final ExecutorService javaThread = Executors.newSingleThreadExecutor();
    
    public BasicContanThread(ContanEngine contanEngine) {
        this.contanEngine = contanEngine;
    }
    
    public ContanEngine getContanEngine() {return contanEngine;}
    
    @Override
    public <T> T runTaskImmediately(Callable<T> task) throws ExecutionException, InterruptedException {
        Future<T> future = javaThread.submit(task);
        return future.get();
    }
    
    
    
}
