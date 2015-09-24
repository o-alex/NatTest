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

import com.google.common.collect.ImmutableMap;
import com.typesafe.config.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.kompics.Channel;
import se.sics.kompics.ChannelFilter;
import se.sics.kompics.Component;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.ConfigurationException;
import se.sics.kompics.ControlPort;
import se.sics.kompics.Handler;
import se.sics.kompics.Init;
import se.sics.kompics.Kompics;
import se.sics.kompics.KompicsEvent;
import se.sics.kompics.Negative;
import se.sics.kompics.Port;
import se.sics.kompics.PortType;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.Timer;
import se.sics.kompics.timer.java.JavaTimer;
import se.sics.nat.NatLauncherProxy;
import se.sics.nat.NatSerializerSetup;
import se.sics.nat.NatSetup;
import se.sics.nat.NatSetupResult;
import se.sics.nat.hp.SHPSerializerSetup;
import se.sics.nat.pm.PMSerializerSetup;
import se.sics.nat.stun.StunSerializerSetup;
import se.sics.nattest.NatTestComp.NatTestInit;
import se.sics.nattest.serializer.NatTestSerializerSetup;
import se.sics.p2ptoolbox.chunkmanager.ChunkManagerSerializerSetup;
import se.sics.p2ptoolbox.croupier.CroupierPort;
import se.sics.p2ptoolbox.croupier.CroupierSerializerSetup;
import se.sics.p2ptoolbox.util.config.SystemConfig;
import se.sics.p2ptoolbox.util.helper.SystemConfigBuilder;
import se.sics.p2ptoolbox.util.nat.NatedTrait;
import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;
import se.sics.p2ptoolbox.util.serializer.BasicSerializerSetup;
import se.sics.p2ptoolbox.util.traits.AcceptedTraits;
import se.sics.p2ptoolbox.util.update.SelfAddressUpdatePort;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class NatTestLauncher extends ComponentDefinition {

    private static Logger LOG = LoggerFactory.getLogger(NatTestLauncher.class);
    private String logPrefix = "";

    private Component timer;
    private Positive<Network> network;
    private Positive<SelfAddressUpdatePort> adrUpdate;
    private Positive<CroupierPort> globalCroupier;
    private Component natTest;

    private SystemConfig systemConfig;
    
    public NatTestLauncher() {
        LOG.info("{}initiating", logPrefix);
        systemSetup();
        subscribe(handleStart, control);
    }

    private Handler handleStart = new Handler<Start>() {
        @Override
        public void handle(Start event) {
            LOG.info("{}starting", logPrefix);
            connectNStartTimer();
            connectNStartNat();
            LOG.info("{}waiting for nat", logPrefix);
        }
    };

    private void connectNStartTimer() {
        timer = create(JavaTimer.class, Init.NONE);
        trigger(Start.event, timer.control());
    }

    private void connectNStartNat() {
        NatSetup natSetup = new NatSetup(new NatTestProxy(),
                timer.getPositive(Timer.class),
                new SystemConfigBuilder(ConfigFactory.load()));
        natSetup.setup();
        natSetup.start(false);
    }

    private void connectNStartApp() {
        natTest = create(NatTestComp.class, new NatTestInit(systemConfig.self));
        connect(natTest.getNegative(Network.class), network);
        connect(natTest.getNegative(SelfAddressUpdatePort.class), adrUpdate);
        connect(natTest.getNegative(CroupierPort.class), globalCroupier);
        trigger(Start.event, natTest.control());
    }

    public class NatTestProxy implements NatLauncherProxy {

        @Override
        public void startApp(NatSetupResult result) {
            NatTestLauncher.this.network = result.network;
            NatTestLauncher.this.adrUpdate = result.adrUpdate;
            NatTestLauncher.this.globalCroupier = result.globalCroupier;
            NatTestLauncher.this.systemConfig = result.systemConfig;
            LOG.info("{}nat started with:{}", logPrefix, result.systemConfig.self);
            NatTestLauncher.this.connectNStartApp();
        }

        @Override
        public <P extends PortType> Positive<P> requires(Class<P> portType) {
            return NatTestLauncher.this.requires(portType);
        }

        @Override
        public <P extends PortType> Negative<P> provides(Class<P> portType) {
            return NatTestLauncher.this.provides(portType);
        }

        @Override
        public Negative<ControlPort> getControlPort() {
            return NatTestLauncher.this.control;
        }

        @Override
        public <T extends ComponentDefinition> Component create(Class<T> definition, Init<T> initEvent) {
            return NatTestLauncher.this.create(definition, initEvent);
        }

        @Override
        public <T extends ComponentDefinition> Component create(Class<T> definition, Init.None initEvent) {
            return NatTestLauncher.this.create(definition, initEvent);
        }

        @Override
        public <P extends PortType> Channel<P> connect(Positive<P> positive, Negative<P> negative) {
            return NatTestLauncher.this.connect(positive, negative);
        }

        @Override
        public <P extends PortType> Channel<P> connect(Positive<P> positive, Negative<P> negative, ChannelFilter filter) {
            return NatTestLauncher.this.connect(positive, negative, filter);
        }

        @Override
        public <P extends PortType> Channel<P> connect(Negative<P> negative, Positive<P> positive) {
            return NatTestLauncher.this.connect(negative, positive);
        }

        @Override
        public <P extends PortType> Channel<P> connect(Negative<P> negative, Positive<P> positive, ChannelFilter filter) {
            return NatTestLauncher.this.connect(negative, positive, filter);
        }

        @Override
        public <P extends PortType> void disconnect(Negative<P> negative, Positive<P> positive) {
            NatTestLauncher.this.disconnect(negative, positive);
        }

        @Override
        public <P extends PortType> void disconnect(Positive<P> positive, Negative<P> negative) {
            NatTestLauncher.this.disconnect(positive, negative);
        }

        @Override
        public <P extends PortType> void trigger(KompicsEvent e, Port<P> p) {
            NatTestLauncher.this.trigger(e, p);
        }

        @Override
        public <E extends KompicsEvent, P extends PortType> void subscribe(Handler<E> handler, Port<P> port) throws ConfigurationException {
            NatTestLauncher.this.subscribe(handler, port);
        }
    }

    private static void systemSetup() {
        int serializerId = 128;
        serializerId = BasicSerializerSetup.registerBasicSerializers(serializerId);
        serializerId = StunSerializerSetup.registerSerializers(serializerId);
        serializerId = CroupierSerializerSetup.registerSerializers(serializerId);
        serializerId = PMSerializerSetup.registerSerializers(serializerId);
        serializerId = SHPSerializerSetup.registerSerializers(serializerId);
        serializerId = NatSerializerSetup.registerSerializers(serializerId);
        serializerId = ChunkManagerSerializerSetup.registerSerializers(serializerId);
        serializerId = NatTestSerializerSetup.registerSerializers(serializerId);

        if (serializerId > 255) {
            throw new RuntimeException("switch to bigger serializerIds, last serializerId:" + serializerId);
        }

        ImmutableMap acceptedTraits = ImmutableMap.of(NatedTrait.class, 0);
        DecoratedAddress.setAcceptedTraits(new AcceptedTraits(acceptedTraits));
    }
    
    public static void main(String[] args) {
        systemSetup();
        
        if (System.getProperty("java.net.preferIPv4Stack") == null
                || System.getProperty("java.net.preferIPv4Stack").equals("false")) {
            LOG.error("java.net.preferIPv4Stack not set");
            System.exit(1);
        }

        if (Kompics.isOn()) {
            Kompics.shutdown();
        }
        Kompics.createAndStart(NatTestLauncher.class, Runtime.getRuntime().availableProcessors(), 20); // Yes 20 is totally arbitrary
        try {
            Kompics.waitForTermination();
        } catch (InterruptedException ex) {
            System.exit(1);
        }
    }
}
