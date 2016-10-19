package com.github.l0rd;

import com.openshift.internal.restclient.ResourceFactory;
import com.openshift.internal.restclient.model.DeploymentConfig;
import com.openshift.internal.restclient.model.Pod;
import com.openshift.internal.restclient.model.Port;
import com.openshift.internal.restclient.model.Service;
import com.openshift.restclient.*;
import com.openshift.restclient.images.DockerImageURI;
import com.openshift.restclient.model.*;
import com.openshift.restclient.model.deploy.DeploymentTriggerType;
import com.openshift.restclient.model.volume.IVolumeMount;
import org.jboss.dmr.ModelNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class Main {

    private static final String VERSION = "v1";
    private static final Logger LOG = LoggerFactory.getLogger(Main.class);
    private static final String PROJECT_NAME = "eclipse-che";
    private static final String WORKSPACE_ID = "xxxx";
    private static final String WORKSPACE_NAME = "che-ws-" + WORKSPACE_ID;
    //    private static final String WORKSPACE_IMAGE_NAME = "codenvy/ubuntu_jdk8";
    private static final String WORKSPACE_IMAGE_NAME = "openshift/hello-openshift";
    //    private static final String WORKSPACE_IMAGE_NAME = "florentbenoit/che-ws-agent";
    private static final String CHE_HOSTNAME = "che.openshift.adb";
    private static final String CHE_SERVICEACCOUNT = "cheserviceaccount";
    public static final long MILLISECONDS_PER_SECOND = 1000;

    public static void main(String[] args) {
        IClient client;
        IResourceFactory factory;

        client = new ClientBuilder("https://openshift.adb:8443/")
                .withUserName("openshift-dev")
                .withPassword("devel")
                .build();

        factory = new ResourceFactory(client);

        // Look for Che project
        List<IProject> list = client.list(ResourceKind.PROJECT);
        IProject cheproject = list.stream().filter(p -> p.getName().equals(PROJECT_NAME)).findFirst().orElse(null);
        if (cheproject == null) {
            System.out.println("Project not found");
            return;
        }

        // Create che-ws service
        IService service = factory.create(VERSION, ResourceKind.SERVICE);
        ((Service) service).setNamespace(cheproject.getNamespace()); //this will be the project's namespace
        ((Service) service).setName(WORKSPACE_NAME + "-service");
        ((Service) service).setType("NodePort");

        List<IServicePort> ports = new ArrayList<>();
        ports.add(PortFactory.createServicePort("sshd", "TCP", 22, 22));
        ports.add(PortFactory.createServicePort("wsagent", "TCP", 4401, 4401));
        ports.add(PortFactory.createServicePort("wsagent-jpda", "TCP", 4403, 4403));
        ports.add(PortFactory.createServicePort("port1", "TCP", 4411, 4411));
        ports.add(PortFactory.createServicePort("tomcat", "TCP", 8080, 8080));
        ports.add(PortFactory.createServicePort("tomcat-jpda", "TCP", 8000, 8000));
        ports.add(PortFactory.createServicePort("port2", "TCP", 9876, 9876));
        service.setPorts(ports);

        service.setSelector("deploymentConfig", (WORKSPACE_NAME + "-dc"));
        LOG.debug(String.format("Stubbing service: %s", service));
        client.create(service);

        // Create che-ws deployment config
        IDeploymentConfig dc = factory.create(VERSION, ResourceKind.DEPLOYMENT_CONFIG);
        ((DeploymentConfig) dc).setName(WORKSPACE_NAME + "-dc");
        ((DeploymentConfig) dc).setNamespace(cheproject.getName());
        dc.setReplicas(1);
        dc.setReplicaSelector("deploymentConfig", WORKSPACE_NAME + "-dc");
        dc.setServiceAccountName(CHE_SERVICEACCOUNT);
        Set<IPort> containerPorts = new HashSet<>();
        putPorts(containerPorts);
        Map<String, String> envVariables = new HashMap<>();
        putEnvVariables(envVariables);
        dc.addContainer(WORKSPACE_NAME,
                new DockerImageURI(WORKSPACE_IMAGE_NAME),
                containerPorts,
                envVariables,
                Collections.emptyList());
        dc.getContainer(WORKSPACE_NAME).setImagePullPolicy("Always");
        Set<IVolumeMount> volumeMounts = new HashSet<>();
//        putVolumesMount(volumeMounts);
        dc.getContainer(WORKSPACE_NAME).setVolumeMounts(volumeMounts);
        dc.addTrigger(DeploymentTriggerType.CONFIG_CHANGE);

//        IPersistentVolume pv = new PersistentVolume(null, client, ResourcePropertiesRegistry.getInstance().get(VERSION, ResourceKind.PERSISTENT_VOLUME));
//        IHostPathVolumeProperties volume = new HostPathVolumeProperties("/tmp/dir");
//        pv.setPersistentVolumeProperties(volume);
//
//        IPersistentVolumeClaimVolumeSource source;
//        source = (IPersistentVolumeClaimVolumeSource)VolumeSource.create(ModelNode.fromJSONString("{\n" +
//                "    \"name\": \"mysource\",\n" +
//                "    \"persistentVolumeClaim\": {\n" +
//                "        \"claimName\": \"myclaim\",\n" +
//                "        \"readOnly\": true\n" +
//                "    }\n" +
//                "}\n"));
//        source.setName("workspace");
//        source.setClaimName("workspace_clamim");
//        source.setReadOnly(false);
//        dc.addVolume(source);

        client.create(dc);

//        Map<String, String> labels = new HashMap<String, String>(){{
//            put("name","backend");
//            put("env","production");
//        }};

        String deployerLabelKey = "openshift.io/deployer-pod-for.name";
        for (int i = 0; i < 40; i++) {
            try {
                Thread.sleep(1000);                 //1000 milliseconds is one second.
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }

            List<IPod> pods = client.list(ResourceKind.POD, cheproject.getNamespace(), Collections.emptyMap());
            long deployPodNum = pods.stream().filter(p -> p.getLabels().keySet().contains(deployerLabelKey)).count();

            if (deployPodNum > 0) {
                System.out.println("Deploying...");
            } else {
                System.out.println("Deployment ended. Pod details:");
                for (IPod pod : pods) {
                    if (pod.getLabels().get("deploymentConfig").equals(dc.getName())) {
                        System.out.println("==============POD INFO===============");
                        ModelNode containerID = ((Pod) pod).getNode().get("status").get("containerStatuses").get(0).get("containerID");
                        System.out.println(containerID.toString().substring(10, 74));
                        System.out.println(pod.getLabels());
                        System.out.println("=====================================");
                    }
                }
                return;
            }
        }

        System.out.println("TIMEOUT");

//        waitForResource(client, ResourceKind.POD, cheproject.getName(), , 5 * MILLISECONDS_PER_SECOND));

//POD_ID=$(oc get pods | grep che-ws | awk '{print $1}')
//oc delete pod/${POD_ID}
//oc delete dc/che-ws-xxxx-dc
//oc delete svc/che-ws-xxxx-service
//oc delete route/che-ws-xxxx-route

    }

    private static void putVolumesMount(Set<IVolumeMount> volumeMounts) {
        IVolumeMount volumeMount1 = new IVolumeMount() {
            public String getName() {
                return "terminal";
            }

            public String getMountPath() {
                return "/mnt/che/terminal";
            }

            public boolean isReadOnly() {
                return true;
            }

            public void setName(String name) {
            }

            public void setMountPath(String path) {
            }

            public void setReadOnly(boolean readonly) {
            }
        };
        volumeMounts.add(volumeMount1);
        IVolumeMount volumeMount2 = new IVolumeMount() {
            public String getName() {
                return "ws-agent";
            }

            public String getMountPath() {
                return "/mnt/che/ws-agent.tar.gz";
            }

            public boolean isReadOnly() {
                return true;
            }

            public void setName(String name) {
            }

            public void setMountPath(String path) {
            }

            public void setReadOnly(boolean readonly) {
            }
        };
        volumeMounts.add(volumeMount2);
        IVolumeMount volumeMount3 = new IVolumeMount() {
            public String getName() {
                return "ws-agent";
            }

            public String getMountPath() {
                return "/home/user/che/workspaces/" + WORKSPACE_ID;
            }

            public boolean isReadOnly() {
                return false;
            }

            public void setName(String name) {
            }

            public void setMountPath(String path) {
            }

            public void setReadOnly(boolean readonly) {
            }
        };
        volumeMounts.add(volumeMount3);
    }

    private static void putPorts(Set<IPort> containerPorts) {
        Port port1 = new Port(new ModelNode());
        port1.setName("ssh");
        port1.setProtocol("TCP");
        port1.setContainerPort(22);
        containerPorts.add(port1);
        Port port2 = new Port(new ModelNode());
        port2.setName("wsagent");
        port2.setProtocol("TCP");
        port2.setContainerPort(4401);
        containerPorts.add(port2);
        Port port3 = new Port(new ModelNode());
        port3.setName("wsagent-jpda");
        port3.setProtocol("TCP");
        port3.setContainerPort(4403);
        containerPorts.add(port3);
        Port port4 = new Port(new ModelNode());
        port4.setName("port1");
        port4.setProtocol("TCP");
        port4.setContainerPort(4411);
        containerPorts.add(port4);
        Port port5 = new Port(new ModelNode());
        port5.setName("tomcat");
        port5.setProtocol("TCP");
        port5.setContainerPort(8080);
        containerPorts.add(port5);
        Port port6 = new Port(new ModelNode());
        port6.setName("tomcat-jpda");
        port6.setProtocol("TCP");
        port6.setContainerPort(8888);
        containerPorts.add(port6);
        Port port7 = new Port(new ModelNode());
        port7.setName("port2");
        port7.setProtocol("TCP");
        port7.setContainerPort(9876);
        containerPorts.add(port7);
    }

    private static void putVolumes(List<String> volumes) {
        volumes.add("/mnt/che/terminal");
        volumes.add("/mnt/che/ws-agent.tar.gz");
        volumes.add("/projects");
    }

    private static void putEnvVariables(Map<String, String> envVariables) {
        envVariables.put("CHE_LOCAL_CONF_DIR","/mnt/che/conf");
        envVariables.put("USER_TOKEN","dummy_token");
        envVariables.put("CHE_API_ENDPOINT","http://172.17.0.3:8080/wsmaster/api");
        envVariables.put("JAVA_OPTS","-Xms256m -Xmx2048m -Djava.security.egd=file:/dev/./urandom");
        envVariables.put("CHE_WORKSPACE_ID","workspaceoqwmufi1poxj455x");
        envVariables.put("CHE_PROJECTS_ROOT","/projects");
//        envVariables.put("PATH","/opt/jdk1.8.0_45/bin:/home/user/apache-maven-3.3.9/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin");
//        envVariables.put("MAVEN_VERSION","3.3.9");
//        envVariables.put("JAVA_VERSION","8u45");
//        envVariables.put("JAVA_VERSION_PREFIX","1.8.0_45");
        envVariables.put("TOMCAT_HOME","/home/user/tomcat8");
        //envVariables.put("JAVA_HOME","/opt/jdk1.8.0_45");
        envVariables.put("M2_HOME","/home/user/apache-maven-3.3.9");
        envVariables.put("TERM","xterm");
        envVariables.put("LANG","en_US.UTF-8");
    }

    /**
     * Wait for the resource to exist for cases where the test is faster
     * then the server in reconciling its existence;
     *
     * @param client
     * @param kind
     * @param namespace
     * @param name
     * @param maxWaitMillis
     * @return The resource or null if the maxWaitMillis was exceeded or the resource doesnt exist
     */
    public static IResource waitForResource(IClient client, String kind, String namespace, String name, long maxWaitMillis) {
        return waitForResource(client, kind, namespace, name, maxWaitMillis, new ReadyConditional() {
            @Override
            public boolean isReady(IResource resource) {
                return resource != null;
            }

        });
    }

    /**
     * Wait for the resource to exist for cases where the test is faster
     * then the server in reconciling its existence;
     *
     * @param client
     * @param kind
     * @param namespace
     * @param name
     * @param maxWaitMillis
     * @param conditional
     * @return
     */
    public static IResource waitForResource(IClient client, String kind, String namespace, String name, long maxWaitMillis, ReadyConditional conditional) {
        IResource resource = null;
        final long timeout = System.currentTimeMillis() + maxWaitMillis;
        do {
            try {
                resource = client.get(kind, name, namespace);
                if (resource != null && conditional != null) {
                    if (conditional.isReady(resource)) {
                        return resource;
                    }
                    resource = null;
                }
            } catch (NotFoundException e) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e1) {
                    throw new RuntimeException(e1);
                }
            }
        } while (resource == null && System.currentTimeMillis() <= timeout);
        return resource;
    }

    /**
     * Interface that can evaluate a resource to determine if its ready
     *
     * @author jeff.cantrill
     */
    public static interface ReadyConditional {

        /**
         * @param resource
         * @return true if the resource is 'ready'
         */
        boolean isReady(IResource resource);
    }

}
