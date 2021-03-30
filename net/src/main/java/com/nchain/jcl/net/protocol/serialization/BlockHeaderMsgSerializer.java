package com.nchain.jcl.net.protocol.serialization;


import com.nchain.jcl.net.protocol.messages.BlockHeaderMsg;
import com.nchain.jcl.net.protocol.messages.HashMsg;
import com.nchain.jcl.net.protocol.serialization.common.DeserializerContext;
import com.nchain.jcl.net.protocol.serialization.common.MessageSerializer;
import com.nchain.jcl.net.protocol.serialization.common.SerializerContext;
import com.nchain.jcl.tools.bytes.ByteArrayReader;
import com.nchain.jcl.tools.bytes.ByteArrayWriter;
import io.bitcoinj.core.Sha256Hash;

/**
 * @author j.pomer@nchain.com
 * Copyright (c) 2018-2020 nChain Ltd
 */
public class BlockHeaderMsgSerializer implements MessageSerializer<BlockHeaderMsg> {

    protected static final int HEADER_LENGTH = 80; // Block header length (up to the "nonce" field, included)

    private static BlockHeaderMsgSerializer instance;

    protected BlockHeaderMsgSerializer() {
    }

    public static BlockHeaderMsgSerializer getInstance() {
        if (instance == null) {
            synchronized (BlockHeaderMsgSerializer.class) {
                instance = new BlockHeaderMsgSerializer();
            }
        }
        return instance;
    }

    @Override
    public BlockHeaderMsg deserialize(DeserializerContext context, ByteArrayReader byteReader) {

        byte[] blockHeaderBytes = byteReader.read(HEADER_LENGTH);

        HashMsg hash = HashMsg.builder().hash(
            Sha256Hash.wrap(
                Sha256Hash.twiceOf(blockHeaderBytes).getBytes()).getBytes()
        ).build();

        ByteArrayReader headerReader = new ByteArrayReader(blockHeaderBytes);

        long version = headerReader.readUint32();
        HashMsg prevBlockHash = HashMsg.builder().hash(HashMsgSerializer.getInstance().deserialize(context, headerReader).getHashBytes()).build();
        HashMsg merkleRoot = HashMsg.builder().hash(HashMsgSerializer.getInstance().deserialize(context, headerReader).getHashBytes()).build();
        long creationTime = headerReader.readUint32();
        long difficultyTarget = headerReader.readUint32();
        long nonce = headerReader.readUint32();
        var transactionCount = VarIntMsgSerializer.getInstance().deserialize(context, byteReader);

        return BlockHeaderMsg.builder()
            .hash(hash)
            .version(version)
            .prevBlockHash(prevBlockHash)
            .merkleRoot(merkleRoot)
            .creationTimestamp(creationTime)
            .difficultyTarget(difficultyTarget)
            .nonce(nonce)
            .transactionCount(transactionCount)
            .build();
    }

    @Override
    public void serialize(SerializerContext context, BlockHeaderMsg message, ByteArrayWriter byteWriter) {
        byteWriter.writeUint32LE(message.getVersion());
        byteWriter.write(message.getPrevBlockHash().getHashBytes());
        byteWriter.write(message.getMerkleRoot().getHashBytes());
        byteWriter.writeUint32LE(message.getCreationTimestamp());
        byteWriter.writeUint32LE(message.getDifficultyTarget());
        byteWriter.writeUint32LE(message.getNonce());
        VarIntMsgSerializer.getInstance().serialize(context, message.getTransactionCount(), byteWriter);
    }

}