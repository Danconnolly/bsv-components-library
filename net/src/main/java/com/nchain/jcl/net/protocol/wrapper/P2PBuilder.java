package com.nchain.jcl.net.protocol.wrapper;

import com.nchain.jcl.net.network.PeerAddress;
import com.nchain.jcl.net.network.config.NetworkConfig;
import com.nchain.jcl.net.network.config.NetworkConfigImpl;
import com.nchain.jcl.net.network.config.provided.NetworkDefaultConfig;
import com.nchain.jcl.net.network.handlers.NetworkHandlerImpl;
import com.nchain.jcl.net.protocol.config.ProtocolBasicConfig;
import com.nchain.jcl.net.protocol.config.ProtocolConfig;
import com.nchain.jcl.net.protocol.config.ProtocolConfigImpl;
import com.nchain.jcl.net.protocol.handlers.blacklist.BlacklistHandler;
import com.nchain.jcl.net.protocol.handlers.blacklist.BlacklistHandlerConfig;
import com.nchain.jcl.net.protocol.handlers.blacklist.BlacklistHandlerImpl;
import com.nchain.jcl.net.protocol.handlers.block.BlockDownloaderHandler;
import com.nchain.jcl.net.protocol.handlers.block.BlockDownloaderHandlerConfig;
import com.nchain.jcl.net.protocol.handlers.block.BlockDownloaderHandlerImpl;
import com.nchain.jcl.net.protocol.handlers.discovery.DiscoveryHandler;
import com.nchain.jcl.net.protocol.handlers.discovery.DiscoveryHandlerConfig;
import com.nchain.jcl.net.protocol.handlers.discovery.DiscoveryHandlerImpl;
import com.nchain.jcl.net.protocol.handlers.handshake.HandshakeHandler;
import com.nchain.jcl.net.protocol.handlers.handshake.HandshakeHandlerConfig;
import com.nchain.jcl.net.protocol.handlers.handshake.HandshakeHandlerImpl;
import com.nchain.jcl.net.protocol.handlers.message.MessageHandler;
import com.nchain.jcl.net.protocol.handlers.message.MessageHandlerConfig;
import com.nchain.jcl.net.protocol.handlers.message.MessageHandlerImpl;
import com.nchain.jcl.net.protocol.handlers.pingPong.PingPongHandler;
import com.nchain.jcl.net.protocol.handlers.pingPong.PingPongHandlerConfig;
import com.nchain.jcl.net.protocol.handlers.pingPong.PingPongHandlerImpl;
import com.nchain.jcl.tools.config.RuntimeConfig;
import com.nchain.jcl.tools.config.provided.RuntimeConfigDefault;
import com.nchain.jcl.tools.handlers.Handler;
import com.nchain.jcl.tools.handlers.HandlerConfig;

import java.time.Duration;
import java.util.*;

import static com.google.common.base.Preconditions.checkState;

/**
 * @author i.fernandez@nchain.com
 * Copyright (c) 2018-2020 nChain Ltd
 *
 * A Builder for the P2P Class.
 */
public class P2PBuilder {

    // For logging:
    private String id;

    // Configurations:
    private RuntimeConfig runtimeConfig = new RuntimeConfigDefault(); // Default...
    private NetworkConfig networkConfig = new NetworkDefaultConfig(); // Default...
    private Integer serverPort; // when running in Server Mode and it might be different for the rest of the network ports


    // Map to store all the Configurations of all the P2P Handlers included in the P2P Wrapper
    //  - The Base Configuration used for ALL of them is stored here:
    private ProtocolBasicConfig basicConfig;
    private ProtocolConfig protocolConfig;

    //  - The specific Configurations ofr each Handler are stored in a Map:
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

    public P2PBuilder serverPort(Integer serverPort) {
        this.serverPort = serverPort;
        return this;
    }

    public P2PBuilder config(ProtocolConfig protocolConfig) {
        checkState((this.handlerConfigs.isEmpty()) && (this.basicConfig == null),
                "The global Configuration must be injected BEFORE any custom Handler or basic configuration");
        this.protocolConfig = protocolConfig;
        this.handlerConfigs = protocolConfig.getHandlersConfig();
        this.basicConfig = protocolConfig.getBasicConfig();
        return this;
    }

    public P2PBuilder config (ProtocolBasicConfig basicConfig) {
        checkState(this.protocolConfig != null, "a global Configuration must be specified first");
        // we set the Protocol Basic Config and we inject it into the general ProtocolConfig...
        this.basicConfig = basicConfig;
        this.protocolConfig = ((ProtocolConfigImpl) protocolConfig).toBuilder().basicConfig(basicConfig).build();

        // We also overwrite the port number on the Network Config:
        this.networkConfig = ((NetworkConfigImpl) this.networkConfig).toBuilder().port(basicConfig.getPort()).build();
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
        checkState(this.protocolConfig != null, "a global Configuration must be specified first");
        handlerConfigs.put(handlerId, handlerConfig);
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

            // We addBytes different built-in handlers:

            // Network Handler...
            // IMPORTANT: We use 0.0.0.0 to allow connections from anywhere

            if (this.serverPort == null) this.serverPort = networkConfig.getPort();
            String serverIp = "0.0.0.0:" + this.serverPort;
            Handler networkHandler = new NetworkHandlerImpl(id, runtimeConfig, networkConfig, PeerAddress.fromIp(serverIp));
            result.put(networkHandler.getId(), networkHandler);

            // P2P Handlers:
            // All the configuration for these default protocol handlers are supposed to have been already set up by
            // calling the "config(ProtocolConfig)" method
            // Now we go through them, and make sure that they are all using the same "basic Configuration" inside...

            // Message Handler...
            MessageHandlerConfig messageConfig =  (MessageHandlerConfig) handlerConfigs.get(MessageHandler.HANDLER_ID);

            // NOTE: The "maxNumberDedicatedConnections" in the MessageHandler is very related to the
            // "maxBlocksInParallel" property of the BlockDownloaderHandler Config, so we assign its value...
            BlockDownloaderHandlerConfig blockConfigTmp = (BlockDownloaderHandlerConfig) handlerConfigs.get(BlockDownloaderHandler.HANDLER_ID);
            messageConfig = messageConfig.toBuilder().basicConfig(this.basicConfig).maxNumberDedicatedConnections(blockConfigTmp.getMaxBlocksInParallel()) .build();

            Handler messageHandler = new MessageHandlerImpl(id, runtimeConfig, messageConfig);
            result.put(messageHandler.getId(), messageHandler);

            // Handshake Handler...
            HandshakeHandlerConfig handshakeConfig = (HandshakeHandlerConfig) handlerConfigs.get(HandshakeHandler.HANDLER_ID);
            handshakeConfig = handshakeConfig.toBuilder().basicConfig(this.basicConfig).build();
            Handler handshakeHandler = new HandshakeHandlerImpl(id, runtimeConfig, handshakeConfig);
            result.put(handshakeHandler.getId(), handshakeHandler);

            // PingPong Handler...
            PingPongHandlerConfig pingPongConfig = (PingPongHandlerConfig) handlerConfigs.get(PingPongHandler.HANDLER_ID);
            pingPongConfig = pingPongConfig.toBuilder().basicConfig(this.basicConfig).build();
            Handler pingPongHandler = new PingPongHandlerImpl(id, runtimeConfig, pingPongConfig);
            result.put(pingPongHandler.getId(), pingPongHandler);

            // Discovery Handler...
            DiscoveryHandlerConfig discoveryConfig = (DiscoveryHandlerConfig) handlerConfigs.get(DiscoveryHandler.HANDLER_ID);
            discoveryConfig = discoveryConfig.toBuilder().basicConfig(this.basicConfig).build();
            Handler discoveryHandler = new DiscoveryHandlerImpl(id, runtimeConfig, discoveryConfig);
            result.put(discoveryHandler.getId(), discoveryHandler);

            // Blacklist Handler...
            BlacklistHandlerConfig blacklistConfig = (BlacklistHandlerConfig) handlerConfigs.get(BlacklistHandler.HANDLER_ID);
            blacklistConfig = blacklistConfig.toBuilder().basicConfig(this.basicConfig).build();
            Handler blacklistHandler = new BlacklistHandlerImpl(id, runtimeConfig, blacklistConfig);
            result.put(blacklistHandler.getId(), blacklistHandler);

            // Block Downloader Handler...
            BlockDownloaderHandlerConfig blockConfig = (BlockDownloaderHandlerConfig) handlerConfigs.get(BlockDownloaderHandler.HANDLER_ID);
            blockConfig = blockConfig.toBuilder().basicConfig(this.basicConfig).build();
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

            // We set up the default built-in Handlers:
            Map<String, Handler> defaultHandlers = createBuiltInHandlers(runtimeConfig, networkConfig, this.handlerConfigs);

            // We set up the P2P (without handlers, for now)
            result = new P2P(id, runtimeConfig, networkConfig, protocolConfig);

            // Now we addBytes all the Handlers to this P2P: The set of handlers to addBytes is a combination of the
            // default ones, plus the custom ones, minus the ones specifically excluded...

            // First, we calculate which Handlers we should addBytes...
            Map<String, Handler> finalHandlersToAdd = new HashMap<>(defaultHandlers);
            for (String handlerId : this.handlersToAdd.keySet())
                finalHandlersToAdd.put(handlerId, this.handlersToAdd.get(handlerId));
            for (String handlerId : handlersToExclude)
                finalHandlersToAdd.remove(handlerId);

            // Now, we just addBytes them, along with their "status Refresh Frequency", if it has been configured:
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
