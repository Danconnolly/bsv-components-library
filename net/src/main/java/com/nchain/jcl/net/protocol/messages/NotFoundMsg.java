package com.nchain.jcl.net.protocol.messages;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.nchain.jcl.net.protocol.messages.common.Message;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author m.jose@nchain.com
 * Copyright (c) 2018-2020 nChain Ltd
 *
 * notfound is a response to a getdata, sent if any requested data items could not be relayed.
 */
public final class NotFoundMsg extends Message {

    public static final String MESSAGE_TYPE = "notfound";
    private static final long MAX_ADDRESSES = 50000;

    private final VarIntMsg count;
    private final List<InventoryVectorMsg> invVectorList;


    /**
     * Creates the InvMessage Object.Use the corresponding byteArray to create the instance.
     */
    protected NotFoundMsg(VarIntMsg count, List<InventoryVectorMsg> invVectorMsgList) {
        this.count = count;
        this.invVectorList = invVectorMsgList.stream().collect(Collectors.toUnmodifiableList());
        init();
    }

    @Override
    protected long calculateLength() {
        long length = count.getLengthInBytes() + count.getValue() * (InventoryVectorMsg.VECTOR_TYPE_LENGTH + HashMsg.HASH_LENGTH);;
        return length;
    }

    @Override
    protected void validateMessage() {
        Preconditions.checkArgument(count.getValue() <= MAX_ADDRESSES,"Inv message too largeMsgs.");
        Preconditions.checkArgument(count.getValue() ==  invVectorList.size(), "InvMessage list and count value are not same.");
    }

    @Override
    public String getMessageType()                      { return MESSAGE_TYPE; }
    public VarIntMsg getCount()                         { return this.count; }
    public List<InventoryVectorMsg> getInvVectorList()  { return this.invVectorList; }

    @Override
    public String toString() {
        return "NotFoundMsg(count=" + this.getCount() + ", invVectorList=" + this.getInvVectorList() + ")";
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(count, invVectorList);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) { return false; }
        if (obj == this) { return true; }
        if (obj.getClass() != getClass()) { return false; }
        NotFoundMsg other = (NotFoundMsg) obj;
        return Objects.equal(this.count, other.count)
                && Objects.equal(this.invVectorList, other.invVectorList);
    }

    public static NotFoundMsgBuilder builder() {
        return new NotFoundMsgBuilder();
    }

    @Override
    public NotFoundMsgBuilder toBuilder() {
        return new NotFoundMsgBuilder()
                    .count(this.count)
                    .invVectorMsgList(this.invVectorList);
    }

    /**
     * Builder
     */
    public static class NotFoundMsgBuilder extends MessageBuilder {
        private VarIntMsg count;
        private List<InventoryVectorMsg> invVectorMsgList;

        NotFoundMsgBuilder() {}

        public NotFoundMsg.NotFoundMsgBuilder count(VarIntMsg count) {
            this.count = count;
            return this;
        }

        public NotFoundMsg.NotFoundMsgBuilder invVectorMsgList(List<InventoryVectorMsg> invVectorMsgList) {
            this.invVectorMsgList = invVectorMsgList;
            return this;
        }

        public NotFoundMsg build() {
            return new NotFoundMsg(count, invVectorMsgList);
        }

    }
}
