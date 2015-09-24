package org.lanternpowered.server.network.forge.message.processor;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.CodecException;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.lanternpowered.server.network.forge.message.handshake.MessageHandshakeOutRegistryData;
import org.lanternpowered.server.network.forge.message.handshake.MessageHandshakeOutRegistryData.Entry;
import org.lanternpowered.server.network.message.Message;
import org.lanternpowered.server.network.message.codec.CodecContext;
import org.lanternpowered.server.network.message.processor.Processor;
import org.lanternpowered.server.network.vanilla.message.processor.play.ProcessorPlayInChannelPayload;
import org.lanternpowered.server.network.vanilla.message.type.play.MessagePlayInOutChannelPayload;

public final class ProcessorHandshakeOutRegistryData implements Processor<MessageHandshakeOutRegistryData> {

    @Override
    public void process(CodecContext context, MessageHandshakeOutRegistryData message, List<Message> output) throws CodecException {
        Iterator<Entry> it = message.getEntries().iterator();
        if (!it.hasNext()) {
            throw new CodecException("There must be at least one entry present!");
        }
        while (it.hasNext()) {
            Entry entry = it.next();
            ByteBuf buf = context.byteBufAlloc().buffer();
            buf.writeByte(ProcessorPlayInChannelPayload.FML_HANDSHAKE_REGISTRY_DATA);
            buf.writeBoolean(it.hasNext());
            Map<String, Integer> ids = entry.getIds();
            context.writeVarInt(buf, ids.size());
            for (Map.Entry<String, Integer> en : ids.entrySet()) {
                context.write(buf, String.class, en.getKey());
                context.writeVarInt(buf, en.getValue());
            }
            List<String> substitutions = entry.getSubstitutions();
            context.writeVarInt(buf, substitutions.size());
            for (String substitution : substitutions) {
                context.write(buf, String.class, substitution);
            }
            output.add(new MessagePlayInOutChannelPayload("FML|HS", buf));
        }
    }
}
