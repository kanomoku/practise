package threadSynchronizedClassThisObject;

public class Synchronized_Thread_Class2 extends Thread {
    private Synchronized_ObjectService objectService;

    public Synchronized_Thread_Class2(Synchronized_ObjectService objectService) {
        this.objectService = objectService;
    }

    @Override
    public void run() {
        objectService.serviceMethod_Class2();
    }
}