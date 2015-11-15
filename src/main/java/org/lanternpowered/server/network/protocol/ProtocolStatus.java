/*
 * This file is part of LanternServer, licensed under the MIT License (MIT).
 *
 * Copyright (c) LanternPowered <https://github.com/LanternPowered/LanternServer>
 * Copyright (c) Contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the Software), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED AS IS, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.lanternpowered.server.network.protocol;

import org.lanternpowered.server.network.message.MessageRegistry;
import org.lanternpowered.server.network.vanilla.message.codec.status.CodecStatusInOutPing;
import org.lanternpowered.server.network.vanilla.message.codec.status.CodecStatusInRequest;
import org.lanternpowered.server.network.vanilla.message.codec.status.CodecStatusOutResponse;
import org.lanternpowered.server.network.vanilla.message.handler.status.HandlerStatusPing;
import org.lanternpowered.server.network.vanilla.message.handler.status.HandlerStatusRequest;
import org.lanternpowered.server.network.vanilla.message.type.status.MessageStatusInOutPing;
import org.lanternpowered.server.network.vanilla.message.type.status.MessageStatusInRequest;
import org.lanternpowered.server.network.vanilla.message.type.status.MessageStatusOutResponse;

public final class ProtocolStatus extends ProtocolBase {

    ProtocolStatus() {
        MessageRegistry inbound = this.inbound();
        MessageRegistry outbound = this.outbound();

        inbound.register(0x00, MessageStatusInRequest.class, CodecStatusInRequest.class, new HandlerStatusRequest());
        inbound.register(0x01, MessageStatusInOutPing.class, CodecStatusInOutPing.class, new HandlerStatusPing());

        outbound.register(0x00, MessageStatusOutResponse.class, CodecStatusOutResponse.class);
        outbound.register(0x01, MessageStatusInOutPing.class, CodecStatusInOutPing.class);
    }
}