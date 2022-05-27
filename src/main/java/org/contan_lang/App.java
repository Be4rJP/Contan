package org.contan_lang;

import org.contan_lang.syntax.exception.ContanParseException;
import org.contan_lang.thread.ContanThread;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args )
    {
        /*
        for (Token token : tokens) {
            if (token instanceof IdentifierToken) {
                System.out.println(((IdentifierToken) token).getIdentifier().name());
            } else {
                System.out.println(token.getText());
            }
        }*/
        
        
        
        String test1 = "\n" +
                "print(20 * 40)\n" +
                "print(20.1 * 40)\n" +
                "print(20.1 * 40.1)";
        
        String test2 = "\n" +
                "import Thread = importJava(\"java.lang.Thread\")\n" +
                "\n" +
                "class TestClass(i, j) {\n" +
                "    \n" +
                "    data sum\n" +
                "\n" +
                "    initialize {\n" +
                "        sum = i + j\n" +
                "    }\n" +
                "\n" +
                "    function test(t) {\n" +
                "        return async {\n" +
                "            \n" +
                "            \n" +
                "            return \"Result is \" + (sum + t)\n" +
                "        }.await()\n" +
                "    }\n" +
                "\n" +
                "}";


        ContanEngine contanEngine = new ContanEngine();
        ContanThread mainThread = contanEngine.getMainThread();

        try {
            ContanModule module1 = contanEngine.compile("test/TestModule1.cntn", test1);
            ContanModule module2 = contanEngine.compile("test/TestModule2.cntn", test2);
            
            //module2.initialize(mainThread);
            module1.initialize(mainThread);
            
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        try {
            contanEngine.getMainThread().shutdownWithAwait(1, TimeUnit.SECONDS);

            for (ContanThread contanThread : contanEngine.getAsyncThreads()) {
                contanThread.shutdownWithAwait(1, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        /*
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }*/
    }
}
