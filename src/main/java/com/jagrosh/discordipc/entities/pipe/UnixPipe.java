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
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.util.HashMap;

public class UnixPipe extends Pipe {

    private static final Logger LOGGER = LogManager.getLogger();
    private final Socket socket;

    UnixPipe(IPCClient ipcClient, HashMap<String, Callback> callbacks, String location) throws IOException {
        super(ipcClient, callbacks);

        this.socket = new Socket();
        this.socket.connect(UnixDomainSocketAddress.of(location));
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public Packet read() throws IOException, JsonParseException {
        InputStream is = this.socket.getInputStream();

        while (is.available() == 0 && this.status == PipeStatus.CONNECTED) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {
            }
        }

        /*byte[] buf = new byte[is.available()];
        is.read(buf, 0, buf.length);
        LOGGER.info(new String(buf));

        if (true) return null;*/

        if (this.status == PipeStatus.DISCONNECTED)
            throw new IOException("Disconnected!");

        if (this.status == PipeStatus.CLOSED)
            return new Packet(Packet.OpCode.CLOSE, null);

        // Read the op and length. Both are signed ints
        byte[] d = new byte[8];
        is.read(d);
        ByteBuffer bb = ByteBuffer.wrap(d);

        Packet.OpCode op = Packet.OpCode.values()[Integer.reverseBytes(bb.getInt())];
        d = new byte[Integer.reverseBytes(bb.getInt())];

        is.read(d);
        Packet p = new Packet(op, new JsonParser().parse(new String(d)).getAsJsonObject());
        LOGGER.debug(String.format("Received packet: %s", p));
        if (this.listener != null)
            this.listener.onPacketReceived(this.ipcClient, p);
        return p;
    }

    @Override
    public void write(byte[] b) throws IOException {
        this.socket.getOutputStream().write(b);
    }

    @Override
    public void close() throws IOException {
        LOGGER.debug("Closing IPC pipe...");
        this.send(Packet.OpCode.CLOSE, new JsonObject(), null);
        this.status = PipeStatus.CLOSED;
        this.socket.close();
    }
}
