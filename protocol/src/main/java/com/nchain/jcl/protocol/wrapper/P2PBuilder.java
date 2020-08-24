package com.nchain.jcl.protocol.wrapper;

import com.nchain.jcl.network.PeerAddress;
import com.nchain.jcl.network.config.NetworkConfig;
import com.nchain.jcl.network.config.NetworkConfigDefault;
import com.nchain.jcl.network.handlers.NetworkHandlerImpl;

import com.nchain.jcl.protocol.config.ProtocolConfig;
import com.nchain.jcl.protocol.config.provided.ProtocolBSVMainConfig;
import com.nchain.jcl.protocol.handlers.blacklist.BlacklistHandler;
import com.nchain.jcl.protocol.handlers.blacklist.BlacklistHandlerConfig;
import com.nchain.jcl.protocol.handlers.blacklist.BlacklistHandlerImpl;
import com.nchain.jcl.protocol.handlers.block.BlockDownloaderHandler;
import com.nchain.jcl.protocol.handlers.block.BlockDownloaderHandlerConfig;
import com.nchain.jcl.protocol.handlers.block.BlockDownloaderHandlerImpl;
import com.nchain.jcl.protocol.handlers.discovery.DiscoveryHandler;
import com.nchain.jcl.protocol.handlers.discovery.DiscoveryHandlerConfig;
import com.nchain.jcl.protocol.handlers.discovery.DiscoveryHandlerImpl;
import com.nchain.jcl.protocol.handlers.handshake.HandshakeHandler;
import com.nchain.jcl.protocol.handlers.handshake.HandshakeHandlerConfig;
import com.nchain.jcl.protocol.handlers.handshake.HandshakeHandlerImpl;
import com.nchain.jcl.protocol.handlers.message.MessageHandler;
import com.nchain.jcl.protocol.handlers.message.MessageHandlerConfig;
import com.nchain.jcl.protocol.handlers.message.MessageHandlerImpl;
import com.nchain.jcl.protocol.handlers.pingPong.PingPongHandler;
import com.nchain.jcl.protocol.handlers.pingPong.PingPongHandlerConfig;
import com.nchain.jcl.protocol.handlers.pingPong.PingPongHandlerImpl;
import com.nchain.jcl.tools.config.RuntimeConfig;
import com.nchain.jcl.tools.config.provided.RuntimeConfigDefault;
import com.nchain.jcl.tools.handlers.Handler;
import com.nchain.jcl.tools.handlers.HandlerConfig;

import static com.google.common.base.Preconditions.*;

import java.time.Duration;
import java.util.*;

/**
 * @author i.fernandez@nchain.com
 * Copyright (c) 2018-2020 Bitcoin Association
 * Distributed under the Open BSV software license, see the accompanying file LICENSE.
 * @date 2020-06-30 13:04
 *
 * A Builder for the P2P Class.
 */
public class P2PBuilder {

    // Default Values for the Min and Max Peers:
    private static final int MIN_PEERS_DEFAULT = 10;
    private static final int MAX_PEERS_DEFAULT = 15;
    // For logging:
    private String id;

    // Configurations:
    private RuntimeConfig runtimeConfig;
    private NetworkConfig networkConfig;

    // port number (if ZERO, the system will pick up a random port)
    private Integer customPort;

    // Range of Peers to handshake with (we store this config here for convenience instead of using the same
    // variables within the HandshakeHandlerConfig, since these are values that are used very frequently
    private Integer minPeers;  // default
    private Integer maxPeers;  // default

    // A wrapper over the built-in handlers configurations
    ProtocolConfig protocolConfig;

    // Map to store all the Configurations of all the P2P Handlers included in the P2P Wrapper
    // This Map is updated with the built-in configurations stored in "protocolConfig", and it can also
    // store other configurations ofr other Handlers:
    private Map<String, HandlerConfig> handlerConfigs = new HashMap<>();

    // Map to store all the Handlers included:
    private Map<String, Handler> handlersToAdd = new HashMap<>();
    private Set<String> handlersToExclude = new HashSet<>();


    // A Map storing the frequency to notify the status of each Handler. (Key: Handler Id, Value: Frequency)
    private Map<String, Duration> stateRefreshFrequencies = new HashMap<>();
    // A default frequency to notify the status of the Handlers:
    private Duration stateDefaultFrequency = null;

    public P2PBuilder(String id) {
        this.id = id;
    }

    /** Sets up the Runtime Configuration, same for all the P2P Handlers */
    public P2PBuilder config(RuntimeConfig runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
        return this;
    }
    /** Sets up the NetworkConfig, just one */
    public P2PBuilder config(NetworkConfig networkConfig) {
        this.networkConfig = networkConfig;
        return this;
    }

    /**
     * It loads a Default Configuration for all the built-in P2P Handlers included in the P2P Wrapper.
     *
     */

    public P2PBuilder config(ProtocolConfig protocolConfig) {
        checkState(handlerConfigs == null || handlerConfigs.size() == 0,
                "You are trying to set a Global Default configuration AFTER a specific Configuration for "
                + " a specific handler as been set up. The Global Default configuration must be set up BEFORE any other.");
        this.protocolConfig = protocolConfig;
        this.handlerConfigs = protocolConfig.getHandlersConfig();
        return this;
    }

    // Convenience methods for assigning the Default built-in P2P Handlers:

    public P2PBuilder config(MessageHandlerConfig config) {
        return config(MessageHandler.HANDLER_ID, config);
    }

    public P2PBuilder config(HandshakeHandlerConfig config) {
        return config(HandshakeHandler.HANDLER_ID, config);
    }

    public P2PBuilder config(PingPongHandlerConfig config) {
        return config(PingPongHandler.HANDLER_ID, config);
    }

    public P2PBuilder config(DiscoveryHandlerConfig config) {
        return config(DiscoveryHandler.HANDLER_ID, config);
    }

    public P2PBuilder config(BlacklistHandlerConfig config) {
        return config(BlacklistHandler.HANDLER_ID, config);
    }

    public P2PBuilder config(BlockDownloaderHandlerConfig config) { return config(BlockDownloaderHandler.HANDLER_ID, config);}

    /** It sets up a specific configuration for a specific protocol Handler, overwritting the default one (if any) */
    public P2PBuilder config(String handlerId, HandlerConfig handlerConfig) {
        checkState(protocolConfig != null, "Before setting up the Configuration of an individual Handler, "
                + "you must set the Global Default Configuration");
        handlerConfigs.put(handlerId, handlerConfig);
        return this;
    }
    /** It sets up the port number the service will be listening to when starting in SERVER_MODE */
    public P2PBuilder port(Integer port) {
        this.customPort = port;
        return this;
    }

    /**
     * It sets up a port number ZERO, which will make the real port number to be picked by by the system randomly.
     * Useful when testing and running diufferent tests in parallel, so we dont have multiple instances of the
     * P2P Service at the same time running on the same port.
     */
    public P2PBuilder randomPort() {
        this.customPort = 0;
        return this;
    }

    public P2PBuilder minPeers(int minPeers) {
        this.minPeers = minPeers;
        return this;
    }

    public P2PBuilder maxPeers(int maxPees) {
        this.maxPeers = maxPees;
        return this;
    }

    public P2PBuilder publishState(String handlerId, Duration stateRefreshFrequency) {
        this.stateRefreshFrequencies.put(handlerId, stateRefreshFrequency);
        return this;
    }

    public P2PBuilder publishStates(Duration stateRefreshFrequency) {
        this.stateDefaultFrequency = stateRefreshFrequency;
        return this;
    }

    private Map<String, Handler> createBuiltInHandlers(RuntimeConfig runtimeConfig, NetworkConfig networkConfig, Map<String, HandlerConfig> handlerConfigs) {
        Map<String, Handler> result = new HashMap<>();
        try {

            // We add different built-in handlers:

            // Network Handler...
            // The port number might have been specified, if that's the case we take that value, otherwise we use the
            // default value
            int port = (customPort != null)? customPort : protocolConfig.getBasicConfig().getPort();
            Handler networkHandler = new NetworkHandlerImpl(id, runtimeConfig, networkConfig, PeerAddress.localhost(port));
            result.put(networkHandler.getId(), networkHandler);

            // P2P Handlers:
            // All the configuration for these default protocol handlers are supposed to have been already set up by
            // calling the "config(ProtocolConfig)" method
            // Now we go through them, and make sure that they are all using the same "basic Configuration" inside...

            // Message Handler...
            MessageHandlerConfig messageConfig =  (MessageHandlerConfig) handlerConfigs.get(MessageHandler.HANDLER_ID);
            messageConfig = messageConfig.toBuilder().basicConfig(this.protocolConfig.getBasicConfig()).build();
            Handler messageHandler = new MessageHandlerImpl(id, runtimeConfig, messageConfig);
            result.put(messageHandler.getId(), messageHandler);

            // Handshake Handler...
            HandshakeHandlerConfig handshakeConfig = (HandshakeHandlerConfig) handlerConfigs.get(HandshakeHandler.HANDLER_ID);
            handshakeConfig = handshakeConfig.toBuilder().basicConfig(this.protocolConfig.getBasicConfig()).build();
            // If custom "minPeers" or "maxPeers" have been set, we use them now:
            if (minPeers != null) handshakeConfig = handshakeConfig.toBuilder().minPeers(OptionalInt.of(minPeers)).build();
            if (maxPeers != null) handshakeConfig = handshakeConfig.toBuilder().maxPeers(OptionalInt.of(maxPeers)).build();
            Handler handshakeHandler = new HandshakeHandlerImpl(id, runtimeConfig, handshakeConfig);
            result.put(handshakeHandler.getId(), handshakeHandler);

            // PingPong Handler...
            PingPongHandlerConfig pingPongConfig = (PingPongHandlerConfig) handlerConfigs.get(PingPongHandler.HANDLER_ID);
            pingPongConfig = pingPongConfig.toBuilder().basicConfig(this.protocolConfig.getBasicConfig()).build();
            Handler pingPongHandler = new PingPongHandlerImpl(id, runtimeConfig, pingPongConfig);
            result.put(pingPongHandler.getId(), pingPongHandler);

            // Discovery Handler...
            DiscoveryHandlerConfig discoveryConfig = (DiscoveryHandlerConfig) handlerConfigs.get(DiscoveryHandler.HANDLER_ID);
            discoveryConfig = discoveryConfig.toBuilder().basicConfig(this.protocolConfig.getBasicConfig()).build();
            Handler discoveryHandler = new DiscoveryHandlerImpl(id, runtimeConfig, discoveryConfig);
            result.put(discoveryHandler.getId(), discoveryHandler);

            // Blacklist Handler...
            BlacklistHandlerConfig blacklistConfig = (BlacklistHandlerConfig) handlerConfigs.get(BlacklistHandler.HANDLER_ID);
            blacklistConfig = blacklistConfig.toBuilder().basicConfig(this.protocolConfig.getBasicConfig()).build();
            Handler blacklistHandler = new BlacklistHandlerImpl(id, runtimeConfig, blacklistConfig);
            result.put(blacklistHandler.getId(), blacklistHandler);

            // Block Downloader Handler...
            BlockDownloaderHandlerConfig blockConfig = (BlockDownloaderHandlerConfig) handlerConfigs.get(BlockDownloaderHandler.HANDLER_ID);
            blockConfig = blockConfig.toBuilder().basicConfig(this.protocolConfig.getBasicConfig()).build();
            Handler blockHandler = new BlockDownloaderHandlerImpl(id, runtimeConfig, blockConfig);
            result.put(blockHandler.getId(), blockHandler);

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        return result;
    }

    public P2PBuilder includeHandler(Handler handler) {
        this.handlersToAdd.put(handler.getId(), handler);
        return this;
    }

    public P2PBuilder excludeHandler(String handlerId) {
        this.handlersToExclude.add(handlerId);
        return this;
    }

    public P2P build() {
        P2P result = null;
        try {


            // We set up the Base Configurations:
            // If the Configurations have been set up, we use them, otherwise we use the default implementations:
            RuntimeConfig runtimeConfig = (this.runtimeConfig != null)? this.runtimeConfig : new RuntimeConfigDefault();
            NetworkConfig networkConfig = (this.networkConfig != null)? this.networkConfig : new NetworkConfigDefault();

            // We set up the Global Default P2P Configuration:
            if (this.protocolConfig == null) config(new ProtocolBSVMainConfig());

            // We set up the default built-in Handlers:
            Map<String, Handler> defaultHandlers = createBuiltInHandlers(runtimeConfig, networkConfig, this.handlerConfigs);

            // We set up the P2P (without handlers, for now)
            result = new P2P(id, runtimeConfig, networkConfig, protocolConfig);

            // Now we add all the Handlers to this P2P: The set of handlers to add is a combination of the
            // default ones, plus the custom ones, minus the ones specifically excluded...

            // First, we calculate which Handlers we should add...
            Map<String, Handler> finalHandlersToAdd = new HashMap<>(defaultHandlers);
            for (String handlerId : this.handlersToAdd.keySet())
                finalHandlersToAdd.put(handlerId, this.handlersToAdd.get(handlerId));
            for (String handlerId : handlersToExclude)
                finalHandlersToAdd.remove(handlerId);

            // Now, we just add them, along with their "status Refresh Frequency", if it has been configured:
            for (Handler handler : finalHandlersToAdd.values()) {
                String handlerId = handler.getId();
                Duration handlerStateFrequency = stateRefreshFrequencies.containsKey(handlerId)
                        ? stateRefreshFrequencies.get(handlerId) : stateDefaultFrequency;
                result.addHandler(handler);
                if (handlerStateFrequency != null)
                    result.refreshHandlerState(handlerId, handlerStateFrequency);
            }

            // We return the result:
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        return result;
    }
}
