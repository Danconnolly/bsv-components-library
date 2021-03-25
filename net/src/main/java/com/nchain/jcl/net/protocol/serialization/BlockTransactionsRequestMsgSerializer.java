package com.nchain.jcl.net.protocol.serialization;

import com.nchain.jcl.net.protocol.messages.BlockTransactionsRequestMsg;
import com.nchain.jcl.net.protocol.messages.VarIntMsg;
import com.nchain.jcl.net.protocol.serialization.common.DeserializerContext;
import com.nchain.jcl.net.protocol.serialization.common.MessageSerializer;
import com.nchain.jcl.net.protocol.serialization.common.SerializerContext;
import com.nchain.jcl.tools.bytes.ByteArrayReader;
import com.nchain.jcl.tools.bytes.ByteArrayWriter;

import java.util.ArrayList;
import java.util.List;

public class BlockTransactionsRequestMsgSerializer implements MessageSerializer<BlockTransactionsRequestMsg> {
    private static BlockTransactionsRequestMsgSerializer instance;

    public static BlockTransactionsRequestMsgSerializer getInstance() {
        if (instance == null) {
            synchronized (BlockTransactionsRequestMsgSerializer.class) {
                instance = new BlockTransactionsRequestMsgSerializer();
            }
        }
        return instance;
    }


    @Override
    public BlockTransactionsRequestMsg deserialize(DeserializerContext context, ByteArrayReader byteReader) {
        var blockHash = byteReader.read(32);
        var indexesLength = VarIntMsgSerializer.getInstance().deserialize(context, byteReader);

        List<VarIntMsg> indexes = new ArrayList<>();
        for (int i = 0; i < indexesLength.getValue(); i++) {
            indexes.add(VarIntMsgSerializer.getInstance().deserialize(context, byteReader));
        }

        return BlockTransactionsRequestMsg.builder()
            .blockHash(blockHash)
            .indexesLength(indexesLength)
            .indexes(indexes)
            .build();
    }

    @Override
    public void serialize(SerializerContext context, BlockTransactionsRequestMsg message, ByteArrayWriter byteWriter) {
        byteWriter.write(message.getBlockHash());
        var varIntMsgSerializer = VarIntMsgSerializer.getInstance();
        varIntMsgSerializer.serialize(context, message.getIndexesLength(), byteWriter);
        message.getIndexes().forEach(index -> varIntMsgSerializer.serialize(context, index, byteWriter));
    }
}
