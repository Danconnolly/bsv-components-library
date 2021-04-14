package com.nchain.jcl.net.protocol.events.data;

import com.nchain.jcl.net.network.PeerAddress;
import com.nchain.jcl.net.protocol.messages.GetBlockTxnMsg;
import com.nchain.jcl.net.protocol.messages.common.BitcoinMsg;

/**
 * @author j.pomer@nchain.com
 * Copyright (c) 2018-2020 nChain Ltd
 *
 * An Event triggered when a @{@link GetBlockTxnMsg} Message is received from a Remote Peer.
 */
public final class GetBlockTxnMsgReceivedEvent extends MsgReceivedEvent<GetBlockTxnMsg> {
    public GetBlockTxnMsgReceivedEvent(PeerAddress peerAddress, BitcoinMsg<GetBlockTxnMsg> btcMsg) {
        super(peerAddress, btcMsg);
    }
}
