package com.nchain.jcl.net.protocol.messages;

import com.nchain.jcl.net.protocol.messages.common.Message;

import java.io.Serializable;

/**
 * @author i.fernandez@nchain.com
 * Copyright (c) 2018-2020 nChain Ltd
 *
 *
 * The verack message is sent in reply to version. This message consists of only a message
 * header with the command string "verack".
 *
 */
public final class VersionAckMsg extends Message implements Serializable {

    // Message Type (stored in the "command" field in the HeaderMsg of a Bitcoin Message
    public static final String MESSAGE_TYPE = "verack";
    private static final int MESSAGE_LENGTH = 0;

    protected VersionAckMsg(long payloadChecksum) {
        super(payloadChecksum);
        init();
    }

    @Override
    public String getMessageType() { return MESSAGE_TYPE; }

    @Override
    public String toString() {
        return "[VersionAck| empty message" + "]";
    }

    @Override
    public int hashCode() {
        return 1;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) { return false; }
        if (obj == this) { return true; }
        if (obj.getClass() != getClass()) { return false; }
        return true;
    }

    @Override
    protected long calculateLength() {
        long length = MESSAGE_LENGTH;
        return length;
    }

    @Override
    protected void validateMessage() {}

    public static VersionAckMsgBuilder builder() {
        return new VersionAckMsgBuilder();
    }

    @Override
    public VersionAckMsgBuilder toBuilder() {
        return new VersionAckMsgBuilder(super.extraBytes, super.payloadChecksum);
    }

    /**
     * Builder
     */
    public static class VersionAckMsgBuilder extends MessageBuilder{
        public VersionAckMsgBuilder() {}
        public VersionAckMsgBuilder(byte[] extraBytes, long payloadChecksum) { super(extraBytes, payloadChecksum);}

        public VersionAckMsg build() {
            return new VersionAckMsg(super.payloadChecksum);
        }
    }
}
