package io.mutator.samples;
import javax.net.SocketFactory;
import java.io.IOException;
import java.net.Socket;
public class SocketSample {
    // Variable declared as SocketFactory so bytecode emits INVOKEVIRTUAL javax/net/SocketFactory.createSocket
    public static Socket connect(SocketFactory factory, String host, int port) throws IOException {
        return factory.createSocket(host, port);
    }
}
