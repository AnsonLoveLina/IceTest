package com.hankcs.network;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ice4j.Transport;
import org.ice4j.TransportAddress;
import org.ice4j.ice.*;
import org.ice4j.ice.harvest.StunCandidateHarvester;
import org.ice4j.ice.harvest.TurnCandidateHarvester;
import org.ice4j.security.LongTermCredential;
import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.List;

public class IceClient {

    private int port;

    private String streamName;

    private Agent agent;

    private String localSdp;

    private String remoteSdp = "";

//    private String[] turnServers = new String[]{"38j77i.natappfree.cc:34459"};
//
//    private String[] stunServers = new String[]{"38j77i.natappfree.cc:34459"};
//
//    private String username = "zy";
//
//    private String password = "xx198742";

    private String[] turnServers = new String[]{"stun.jitsi.net:3478"};

    private String[] stunServers = new String[]{"stun.stunprotocol.org:3478"};

    private String username = "guest";

    private String password = "anonymouspower!!";

    private IceProcessingListener listener;

    static Logger log = LogManager.getLogger(IceClient.class);

    public IceClient(int port, String streamName) {
        this.port = port;
        this.streamName = streamName;
        this.listener = new IceProcessingListener();
    }

    public void init() throws Throwable {

        agent = createAgent(port, streamName);

        agent.setNominationStrategy(NominationStrategy.NOMINATE_HIGHEST_PRIO);

        agent.addStateChangeListener(listener);

        agent.setControlling(false);

        agent.setTa(10000);

        localSdp = SdpUtils.createSDPDescription(agent);

        log.info("=================== feed the following"
                + " to the remote agent ===================");

        System.out.println(localSdp);

        WebSocketClient webSocketClient =
                new WebSocketClient(new URI("ws://192.168.1.120:7080/exchangeSDP"), new Draft_6455()) {

                    @Override
                    public void onOpen(ServerHandshake arg0) {
                        System.out.println("打开链接");
                    }

                    @Override
                    public void onMessage(String arg0) {
                        synchronized (remoteSdp) {
                            System.out.println("arg0 = " + arg0);
                            remoteSdp = arg0;
                            remoteSdp.notifyAll();
                        }
                    }

                    @Override
                    public void onError(Exception arg0) {
                        arg0.printStackTrace();
                        System.out.println("发生错误已关闭");
                    }

                    @Override
                    public void onClose(int arg0, String arg1, boolean arg2) {
                        System.out.println("链接已关闭");
                    }

                    @Override
                    public void onMessage(ByteBuffer bytes) {
                        try {
                            System.out.println(new String(bytes.array(), "utf-8"));
                        } catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                        }
                    }


                };
        webSocketClient.connect();
        while (!webSocketClient.getReadyState().equals(WebSocket.READYSTATE.OPEN)) {
            System.out.println("还没有打开");
        }
        webSocketClient.send(localSdp);

        log.info("======================================"
                + "========================================\n");
    }

    public DatagramSocket getDatagramSocket() throws Throwable {

        LocalCandidate localCandidate = agent
                .getSelectedLocalCandidate(streamName);

        IceMediaStream stream = agent.getStream(streamName);
        List<Component> components = stream.getComponents();
        for (Component c : components) {
            log.info(c);
        }
        log.info(localCandidate.toString());
        LocalCandidate candidate = (LocalCandidate) localCandidate;
        return candidate.getDatagramSocket();

    }

    public SocketAddress getRemotePeerSocketAddress() {
        RemoteCandidate remoteCandidate = agent
                .getSelectedRemoteCandidate(streamName);
        log.info("Remote candinate transport address:"
                + remoteCandidate.getTransportAddress());
        log.info("Remote candinate host address:"
                + remoteCandidate.getHostAddress());
        log.info("Remote candinate mapped address:"
                + remoteCandidate.getMappedAddress());
        log.info("Remote candinate relayed address:"
                + remoteCandidate.getRelayedAddress());
        log.info("Remote candinate reflexive address:"
                + remoteCandidate.getReflexiveAddress());
        return remoteCandidate.getTransportAddress();
    }

    /**
     * Reads an SDP description from the standard input.In production
     * environment that we can exchange SDP with peer through signaling
     * server(SIP server)
     */
    public void exchangeSdpWithPeer() throws Throwable {
        log.info("Paste remote SDP here. Enter an empty line to proceed:");
//        BufferedReader reader = new BufferedReader(new InputStreamReader(
//                System.in));
//
//        StringBuilder buff = new StringBuilder();
//        String line = new String();
//
//        while ((line = reader.readLine()) != null)
//        {
//            line = line.trim();
//            if (line.length() == 0)
//            {
//                break;
//            }
//            buff.append(line);
//            buff.append("\r\n");
//        }
//
//        remoteSdp = buff.toString();
        System.out.println("remoteSdp: "+remoteSdp);

        synchronized (remoteSdp) {
            if (StringUtils.isEmpty(remoteSdp)) {
                remoteSdp.wait();
            }
        }

        SdpUtils.parseSDP(agent, remoteSdp);
    }

    public void startConnect() throws InterruptedException {

        if (StringUtils.isBlank(remoteSdp)) {
            throw new NullPointerException(
                    "Please exchange sdp information with peer before start connect! ");
        }

        agent.startConnectivityEstablishment();

        // agent.runInStunKeepAliveThread();

        synchronized (listener) {
            listener.wait();
        }

    }

    private Agent createAgent(int rtpPort, String streamName) throws Throwable {
        return createAgent(rtpPort, streamName, false);
    }

    private Agent createAgent(int rtpPort, String streamName,
                              boolean isTrickling) throws Throwable {

        long startTime = System.currentTimeMillis();

        Agent agent = new Agent();

        agent.setTrickling(isTrickling);

        // STUN
        for (String server : stunServers) {
            String[] pair = server.split(":");
            agent.addCandidateHarvester(new StunCandidateHarvester(
                    new TransportAddress(pair[0], Integer.parseInt(pair[1]),
                            Transport.UDP)));
        }

        // TURN
        LongTermCredential longTermCredential = new LongTermCredential(username,
                password);

        for (String server : turnServers) {
            String[] pair = server.split(":");
            agent.addCandidateHarvester(new TurnCandidateHarvester(
                    new TransportAddress(pair[0], Integer.parseInt(pair[1]), Transport.UDP),
                    longTermCredential));
        }
        // STREAMS
        createStream(rtpPort, streamName, agent);

        long endTime = System.currentTimeMillis();
        long total = endTime - startTime;

        log.info("Total harvesting time: " + total + "ms.");

        return agent;
    }

    private IceMediaStream createStream(int rtpPort, String streamName,
                                        Agent agent) throws Throwable {
        long startTime = System.currentTimeMillis();
        IceMediaStream stream = agent.createMediaStream(streamName);
        // rtp
        Component component = agent.createComponent(stream, Transport.UDP,
                rtpPort, rtpPort, rtpPort + 100);

        long endTime = System.currentTimeMillis();
        log.info("Component Name:" + component.getName());
        log.info("RTP Component created in " + (endTime - startTime) + " ms");

        return stream;
    }

    /**
     * Receive notify event when ice processing state has changed.
     */
    public static final class IceProcessingListener implements
            PropertyChangeListener {

        private long startTime = System.currentTimeMillis();

        public void propertyChange(PropertyChangeEvent event) {

            Object state = event.getNewValue();

            log.info("Agent entered the " + state + " state.");
            if (state == IceProcessingState.COMPLETED) {
                long processingEndTime = System.currentTimeMillis();
                log.info("Total ICE processing time: "
                        + (processingEndTime - startTime) + "ms");
                Agent agent = (Agent) event.getSource();
                List<IceMediaStream> streams = agent.getStreams();

                for (IceMediaStream stream : streams) {
                    log.info("Stream name: " + stream.getName());
                    List<Component> components = stream.getComponents();
                    for (Component c : components) {
                        log.info("------------------------------------------");
                        log.info("Component of stream:" + c.getName()
                                + ",selected of pair:" + c.getSelectedPair());
                        log.info("------------------------------------------");
                    }
                }

                log.info("Printing the completed check lists:");
                for (IceMediaStream stream : streams) {

                    log.info("Check list for  stream: " + stream.getName());

                    log.info("nominated check list:" + stream.getCheckList());
                }
                synchronized (this) {
                    this.notifyAll();
                }
            } else if (state == IceProcessingState.TERMINATED) {
                log.info("ice processing TERMINATED");
            } else if (state == IceProcessingState.FAILED) {
                log.info("ice processing FAILED");
                ((Agent) event.getSource()).free();
            }
        }
    }

    public String[] getTurnServers() {
        return turnServers;
    }

    public void setTurnServers(String[] turnServers) {
        this.turnServers = turnServers;
    }

    public String[] getStunServers() {
        return stunServers;
    }

    public void setStunServers(String[] stunServers) {
        this.stunServers = stunServers;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}