package com.nchain.jcl.net.protocol.serialization;

import com.nchain.jcl.base.tools.bytes.ByteArrayReader;
import com.nchain.jcl.base.tools.bytes.ByteArrayWriter;
import com.nchain.jcl.net.protocol.serialization.common.DeserializerContext;
import com.nchain.jcl.net.protocol.serialization.common.MessageSerializer;
import com.nchain.jcl.net.protocol.serialization.common.SerializerContext;
import com.nchain.jcl.net.protocol.messages.TxOutputMsg;

/**
 * @author m.jose@nchain.com
 * Copyright (c) 2018-2019 Bitcoin Association
 * Distributed under the Open BSV software license, see the accompanying file LICENSE.
 *
 * @date 01/10/2019
 *
 * A Serializer for instance of {@Link TxOutputMessage} messages
 */
public class TxOutputMsgSerializer implements MessageSerializer<TxOutputMsg> {

    private static TxOutputMsgSerializer instance;

    // Reference to singleton instances used during serialization/Deserialization. Defined here for performance
    private static VarIntMsgSerializer      varIntMsgSerializer     = VarIntMsgSerializer.getInstance();

    private TxOutputMsgSerializer() { }

    public static TxOutputMsgSerializer getInstance(){
        if(instance == null) {
            synchronized (TxOutputMsgSerializer.class) {
                instance = new TxOutputMsgSerializer();
            }
        }

        return instance;
    }

    @Override
    public TxOutputMsg deserialize(DeserializerContext context, ByteArrayReader byteReader) {

        byteReader.waitForBytes(8);
        long txValue = byteReader.readInt64LE();
        int scriptLen = (int) varIntMsgSerializer.deserialize(context, byteReader).getValue();

        byteReader.waitForBytes(scriptLen);
        byte[] pk_script = byteReader.read(scriptLen);

        return TxOutputMsg.builder().txValue(txValue).pk_script(pk_script).build();
    }

    @Override
    public void serialize(SerializerContext context, TxOutputMsg message, ByteArrayWriter byteWriter) {
        byteWriter.writeUint64LE(message.getTxValue());
        varIntMsgSerializer.serialize(context, message.getPk_script_length(), byteWriter);
        byte[] script = (message.getPk_script_length().getValue() > 0) ? message.getPk_script() : new byte[]{};
        byteWriter.write(script);
    }
}
