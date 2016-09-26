public class HelloWorld {
    static {
        System.loadLibrary("user");
    }

    public native static void showHello();

    public static void main(String[] args) {
        HelloWorld helloWorld = new HelloWorld();
        helloWorld.showHello();
    }
}
