/*
 * Copyright (C) 2009 Swedish Institute of Computer Science (SICS) Copyright (C)
 * 2009 Royal Institute of Technology (KTH)
 *
 * NatTraverser is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package se.sics.nattest.serializer;

import se.sics.kompics.network.netty.serialization.Serializers;
import se.sics.nattest.msg.NatTestMsg;
import se.sics.p2ptoolbox.util.serializer.BasicSerializerSetup;

/**
 *
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class NatTestSerializerSetup {

    public static int serializerIds = 2;

    public static enum NatTestSerializers {

        Ping(NatTestMsg.Ping.class, "natTestPingSerializer"),
        Pong(NatTestMsg.Pong.class, "natTestPongSerializer");

        public final Class serializedClass;
        public final String serializerName;

        private NatTestSerializers(Class serializedClass, String serializerName) {
            this.serializedClass = serializedClass;
            this.serializerName = serializerName;
        }
    }

    public static void checkSetup() {
        for (NatTestSerializers cs : NatTestSerializers.values()) {
            if (Serializers.lookupSerializer(cs.serializedClass) == null) {
                throw new RuntimeException("No serializer for " + cs.serializedClass);
            }
        }
        BasicSerializerSetup.checkSetup();
    }

    public static int registerSerializers(int startingId) {
        int currentId = startingId;

        NatTestMsgSerializer.Ping pingSerializer = new NatTestMsgSerializer.Ping(currentId++);
        Serializers.register(pingSerializer, NatTestSerializers.Ping.serializerName);
        Serializers.register(NatTestSerializers.Ping.serializedClass, NatTestSerializers.Ping.serializerName);

        NatTestMsgSerializer.Pong pongSerializer = new NatTestMsgSerializer.Pong(currentId++);
        Serializers.register(pongSerializer, NatTestSerializers.Pong.serializerName);
        Serializers.register(NatTestSerializers.Pong.serializedClass, NatTestSerializers.Pong.serializerName);

        assert startingId + serializerIds == currentId;
        return currentId;
    }
}
