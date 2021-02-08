package com.nchain.jcl.net.network.events;

import com.nchain.jcl.net.network.PeerAddress;
import com.nchain.jcl.tools.events.Event;

import java.util.List;

/**
 * @author i.fernandez@nchain.com
 * Copyright (c) 2018-2020 nChain Ltd
 *
 * An Event that represents a Request to Connect to a List of Peers.
 */
public final class ConnectPeersRequest extends Event {
    private final List<PeerAddress> peerAddressList;

    public ConnectPeersRequest(List<PeerAddress> peerAddressList)   { this.peerAddressList = peerAddressList; }
    public List<PeerAddress> getPeerAddressList()                   { return this.peerAddressList; }

    public String toString() {
        return "ConnectPeersRequest(peerAddressList=" + this.getPeerAddressList() + ")";
    }
}
