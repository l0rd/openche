package com.github.l0rd;

import com.openshift.internal.restclient.model.ServicePort;
import org.jboss.dmr.ModelNode;

public class PortFactory {

    public static ServicePort createServicePort(String name, String proto, int port, int targetPort) {
        return createServicePort(name, proto, port, String.valueOf(targetPort));
    }

    public static ServicePort createServicePort(String name, String proto, int port, String targetPort) {
        ModelNode node = new ModelNode();
        node.get("name").set(name);
        node.get("protocol").set(proto);
        node.get("port").set(port);
        node.get("targetPort").set(targetPort);
        return new ServicePort(node);
    }
}
