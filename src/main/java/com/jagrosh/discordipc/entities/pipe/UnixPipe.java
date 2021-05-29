/*
 * Copyright 2017 John Grosh (john.a.grosh@gmail.com).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jagrosh.discordipc.entities.pipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.jagrosh.discordipc.IPCClient;
import com.jagrosh.discordipc.entities.Callback;
import com.jagrosh.discordipc.entities.Packet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.HashMap;

public class UnixPipe extends Pipe {

    private static final Logger LOGGER = LogManager.getLogger();
    private final SocketChannel channel;

    UnixPipe(IPCClient ipcClient, HashMap<String, Callback> callbacks, String location) throws IOException {
        super(ipcClient, callbacks);

        this.channel = SocketChannel.open(StandardProtocolFamily.UNIX);
        this.channel.connect(UnixDomainSocketAddress.of(location));
    }

    @Override
    public Packet read() throws IOException, JsonParseException {
        if (this.status == PipeStatus.DISCONNECTED)
            throw new IOException("Disconnected!");

        if (this.status == PipeStatus.CLOSED)
            return new Packet(Packet.OpCode.CLOSE, null);

        // Read the op and length. Both are signed ints
        ByteBuffer buf = ByteBuffer.allocate(8);
        while (buf.position() < buf.capacity()) {
            this.channel.read(buf);
        }
        buf.flip();

        Packet.OpCode op = Packet.OpCode.values()[Integer.reverseBytes(buf.getInt())];
        buf = ByteBuffer.allocate(Integer.reverseBytes(buf.getInt()));
        while (buf.position() < buf.capacity()) {
            this.channel.read(buf);
        }
        buf.flip();

        Packet p = new Packet(op, new JsonParser().parse(new String(buf.array())).getAsJsonObject());
        LOGGER.debug(String.format("Received packet: %s", p));
        if (this.listener != null)
            this.listener.onPacketReceived(this.ipcClient, p);
        return p;
    }

    @Override
    public void write(byte[] b) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(b.length);
        buf.put(b);
        buf.flip();
        while (buf.hasRemaining()) {
            this.channel.write(buf);
        }
    }

    @Override
    public void close() throws IOException {
        LOGGER.debug("Closing IPC pipe...");
        this.send(Packet.OpCode.CLOSE, new JsonObject(), null);
        this.status = PipeStatus.CLOSED;
        this.channel.close();
    }
}
