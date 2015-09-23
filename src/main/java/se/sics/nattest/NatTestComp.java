/*
 * Copyright (C) 2009 Swedish Institute of Computer Science (SICS) Copyright (C)
 * 2009 Royal Institute of Technology (KTH)
 *
 * NatTest is free software; you can redistribute it and/or
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
package se.sics.nattest;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.kompics.ClassMatchedHandler;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Init;
import se.sics.kompics.Kompics;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.network.Msg;
import se.sics.kompics.network.Network;
import se.sics.kompics.network.Transport;
import se.sics.nattest.msg.NatTestMsg;
import se.sics.p2ptoolbox.croupier.CroupierPort;
import se.sics.p2ptoolbox.croupier.msg.CroupierSample;
import se.sics.p2ptoolbox.util.Container;
import se.sics.p2ptoolbox.util.nat.NatedTrait;
import se.sics.p2ptoolbox.util.network.ContentMsg;
import se.sics.p2ptoolbox.util.network.impl.BasicContentMsg;
import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;
import se.sics.p2ptoolbox.util.network.impl.DecoratedHeader;
import se.sics.p2ptoolbox.util.update.SelfAddress;
import se.sics.p2ptoolbox.util.update.SelfAddressUpdate;
import se.sics.p2ptoolbox.util.update.SelfAddressUpdatePort;

/**
 *
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class NatTestComp extends ComponentDefinition {

    private static int counter = 10;
    private static final Logger LOG = LoggerFactory.getLogger(NatTestComp.class);
    private String logPrefix = "";

    private Positive<Network> network = requires(Network.class);
    private Positive<SelfAddressUpdatePort> selfAddress = requires(SelfAddressUpdatePort.class);
    private Positive<CroupierPort> croupier = requires(CroupierPort.class);

    private DecoratedAddress self = null;
    private final Set<String> pinged = new HashSet<String>();
    private final Set<String> ponged = new HashSet<String>();

    public NatTestComp() {
        this.logPrefix = self.getBase() + " ";
        LOG.info("{}initiating with self:{} ping:{}",
                new Object[]{logPrefix});

        subscribe(handleStart, control);
    }

    Handler handleStart = new Handler<Start>() {
        @Override
        public void handle(Start event) {
            LOG.info("{}starting", logPrefix);
            trigger(new SelfAddress.Request(UUID.randomUUID()), selfAddress);
        }
    };

    Handler handleSelfAddressResponse = new Handler<SelfAddress.Response>() {
        @Override
        public void handle(SelfAddress.Response resp) {
            LOG.info("{}update self:{}", logPrefix, resp.selfAddress);
            self = resp.selfAddress;
            subscribe(handleCroupierSample, croupier);
            subscribe(handlePing, network);
            subscribe(handlePong, network);
            subscribe(handleSelfAddressUpdate, selfAddress);
        }
    };

    Handler handleSelfAddressUpdate = new Handler<SelfAddressUpdate>() {
        @Override
        public void handle(SelfAddressUpdate update) {
            LOG.info("{}update self:{}", logPrefix, update.self);
            self = update.self;
        }
    };

    Handler handleCroupierSample = new Handler<CroupierSample<Object>>() {
        @Override
        public void handle(CroupierSample<Object> sample) {
            LOG.info("{}received public:{}, private:{}",
                    new Object[]{logPrefix, sample.publicSample, sample.privateSample});
            LOG.info("{}nat:{}", logPrefix, self.getTrait(NatedTrait.class).natToString());
            LOG.info("{}pinged:{}", logPrefix, pinged);
            LOG.info("{}ponged:{}", logPrefix, ponged);
            counter--;
            if (counter == 0) {
                Kompics.forceShutdown();
            }
            for (Container<DecoratedAddress, Object> target : sample.publicSample) {
                if (!ponged.contains(target.getSource().getTrait(NatedTrait.class).natToString())) {
                    pinged.add(target.getSource().getTrait(NatedTrait.class).natToString());
                    DecoratedHeader<DecoratedAddress> pingHeader = new DecoratedHeader(self, target.getSource(), Transport.UDP);
                    ContentMsg pingMsg = new BasicContentMsg(pingHeader, new NatTestMsg.Ping());
                    LOG.info("{}pinging from:{} to:{}", new Object[]{logPrefix, self, target.getSource()});
                    trigger(pingMsg, network);
                }
            }
            for (Container<DecoratedAddress, Object> target : sample.privateSample) {
                if (!ponged.contains(target.getSource().getTrait(NatedTrait.class).natToString())) {
                    pinged.add(target.getSource().getTrait(NatedTrait.class).natToString());
                    DecoratedHeader<DecoratedAddress> pingHeader = new DecoratedHeader(self, target.getSource(), Transport.UDP);
                    ContentMsg pingMsg = new BasicContentMsg(pingHeader, new NatTestMsg.Ping());
                    LOG.info("{}pinging from:{} to:{}", new Object[]{logPrefix, self, target.getSource()});
                    trigger(pingMsg, network);
                }
            }
        }
    };

    ClassMatchedHandler handlePing
            = new ClassMatchedHandler<NatTestMsg.Ping, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, NatTestMsg.Ping>>() {
                @Override
                public void handle(NatTestMsg.Ping content, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, NatTestMsg.Ping> container) {
                    LOG.info("{}ping from:{} on:{}",
                            new Object[]{logPrefix, container.getSource(), container.getDestination()});
                    DecoratedHeader<DecoratedAddress> pongHeader = new DecoratedHeader(self, container.getSource(), Transport.UDP);
                    ContentMsg pongMsg = new BasicContentMsg(pongHeader, new NatTestMsg.Pong());
                    trigger(pongMsg, network);
                }
            };

    ClassMatchedHandler handlePong
            = new ClassMatchedHandler<NatTestMsg.Pong, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, NatTestMsg.Pong>>() {
                @Override
                public void handle(NatTestMsg.Pong content, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, NatTestMsg.Pong> container) {
                    LOG.info("{}pong from:{} on:{}",
                            new Object[]{logPrefix, container.getSource(), container.getDestination()});
                    ponged.add(container.getSource().getTrait(NatedTrait.class).natToString());
                }
            };

}
