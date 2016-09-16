package io.github.elytra.concrete;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.base.Throwables;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multiset;

import io.github.elytra.concrete.exception.BadMessageException;
import io.github.elytra.concrete.exception.WrongSideException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.FMLNetworkEvent.ClientCustomPacketEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent.ServerCustomPacketEvent;
import net.minecraftforge.fml.common.network.internal.FMLProxyPacket;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class NetworkContext {
	static final Logger log = LogManager.getLogger("Concrete");
	
	protected static final Instanciator instanciator;
	
	static {
		boolean hasMethodHandles;
		try {
			Class.forName("java.lang.invoke.MethodHandles");
			hasMethodHandles = true;
		} catch (Exception e) {
			hasMethodHandles = false;
		}
		if (hasMethodHandles) {
			instanciator = new MethodHandlesInstanciator();
		} else {
			instanciator = new ReflectionInstanciator();
		}
	}
	
	protected final BiMap<Class<? extends Message>, Integer> packetIds = HashBiMap.create();
	protected final Map<Class<? extends Message>, List<WireField<?>>> marshallers = Maps.newHashMap();
	protected final Multiset<Class<? extends Message>> booleanCount = HashMultiset.create();
	
	protected final String channel;
	
	private int nextPacketId = 0;
	
	private NetworkContext(String channel) {
		if (NetworkContext.class.getPackage().getName().equals("io.github.elytra.concrete")
				&& !((Boolean)Launch.blackboard.get("fml.deobfuscatedEnvironment"))) {
			throw new RuntimeException("Concrete is designed to be shaded and must not be left in the default package! (Offending mod: "+Loader.instance().activeModContainer().getName()+")");
		} else {
			log.warn("Concrete is in the default package. This is not a fatal error, as you are in a development environment, but remember to repackage it!");
		}
		this.channel = channel;
		NetworkRegistry.INSTANCE.newEventDrivenChannel(channel).register(this);;
	}
	
	public NetworkContext register(Class<? extends Message> clazz) {
		if (packetIds.containsKey(clazz)) {
			log.warn("{} was registered twice", clazz);
			return this;
		}
		packetIds.put(clazz, nextPacketId++);
		List<WireField<?>> fields = Lists.newArrayList();
		Class<?> cursor = clazz;
		while (cursor != null && cursor != Object.class) {
			for (Field f : cursor.getDeclaredFields()) {
				if (!Modifier.isTransient(f.getModifiers()) && !Modifier.isStatic(f.getModifiers())) {
					if (f.getType() == Boolean.TYPE) {
						booleanCount.add(clazz);
					}
					WireField<?> wf = new WireField<>(f);
					fields.add(wf);
				}
			}
			cursor = cursor.getSuperclass();
		}
		marshallers.put(clazz, fields);
		return this;
	}
	
	
	public String getChannel() {
		return channel;
	}
	
	
	
	protected FMLProxyPacket getPacketFrom(Message m) {
		if (!packetIds.containsKey(m.getClass())) throw new BadMessageException(m.getClass()+" is not registered");
		PacketBuffer payload = new PacketBuffer(Unpooled.buffer());
		payload.writeByte(packetIds.get(m.getClass()));
		int bools = booleanCount.count(m.getClass());
		if (bools > 0) {
			Iterator<WireField<Boolean>> iter = (Iterator)Iterators.filter(marshallers.get(m.getClass()).iterator(), (it) -> it.getType() == Boolean.TYPE);
			List<WireField<Boolean>> li = Lists.newArrayList(iter);
			for (int i = 0; i < (bools+7)/8; i++) {
				int by = 0;
				for (int j = i*8; j < Math.min(li.size(), i+8); j++) {
					WireField<Boolean> wf = li.get(j);
					if (wf.get(m)) {
						by |= (1 << j);
					}
				}
				payload.writeByte(by);
			}
		}
		Iterator<WireField<?>> iter = Iterators.filter(marshallers.get(m.getClass()).iterator(), (it) -> it.getType() != Boolean.TYPE);
		while (iter.hasNext()) {
			WireField<?> wf = iter.next();
			wf.marshal(m, payload);
		}
		return new FMLProxyPacket(payload, channel);
	}


	@SubscribeEvent
	public void onServerCustomPacket(ServerCustomPacketEvent e) {
		ByteBuf payload = e.getPacket().payload();
		Message m = readPacket(e.side(), payload);
		m.doHandleServer(((NetHandlerPlayServer)e.getHandler()).playerEntity);
	}
	
	@SubscribeEvent
	@SideOnly(Side.CLIENT)
	public void onClientCustomPacket(ClientCustomPacketEvent e) {
		ByteBuf payload = e.getPacket().payload();
		Message m = readPacket(e.side(), payload);
		m.doHandleClient();
	}
	
	
	private Message readPacket(Side side, ByteBuf payload) {
		int id = payload.readUnsignedByte();
		if (!packetIds.containsValue(id)) {
			throw new IllegalArgumentException("Unknown packet id "+id);
		}
		Class<? extends Message> clazz = packetIds.inverse().get(id);
		Message m;
		try {
			m = instanciator.instanciate(clazz, this);
		} catch (Throwable t) {
			throw new BadMessageException("Cannot instanciate message class "+clazz, t);
		}
		if (m.getSide() != side) {
			throw new WrongSideException("Cannot receive packet of type "+clazz+" on side "+side);
		}
		int bools = booleanCount.count(clazz);
		if (bools > 0) {
			Iterator<WireField<Boolean>> iter = (Iterator)Iterators.filter(marshallers.get(m.getClass()).iterator(), (it) -> it.getType() == Boolean.TYPE);
			List<WireField<Boolean>> li = Lists.newArrayList(iter);
			for (int i = 0; i < (bools+7)/8; i++) {
				int by = payload.readUnsignedByte();
				for (int j = i*8; j < Math.min(li.size(), i+8); j++) {
					boolean val = (by & (1 << (j-i))) != 0;
					li.get(j).set(m, val);
				}
			}
		}
		Iterator<WireField<?>> iter = Iterators.filter(marshallers.get(m.getClass()).iterator(), (it) -> it.getType() != Boolean.TYPE);
		while (iter.hasNext()) {
			WireField<?> wf = iter.next();
			wf.unmarshal(m, payload);
		}
		return m;
	}
	
	
	public static NetworkContext forChannel(String channel) {
		if (channel.length() > 20)
			throw new IllegalArgumentException("Channel name too long, must be at most 20 characters");
		return new NetworkContext(channel);
	}
	
	
	
	public static class ReflectionInstanciator implements Instanciator {
		private Map<Class<?>, Constructor<?>> constructors = Maps.newHashMap();
		
		@Override
		public <T> T instanciate(Class<T> clazz, NetworkContext nc) {
			try {
				if (!constructors.containsKey(clazz)) {
					Constructor<T> cons = clazz.getConstructor(NetworkContext.class);
					constructors.put(clazz, cons);
				}
				return (T)constructors.get(clazz).newInstance(nc);
			} catch (Throwable e) {
				throw Throwables.propagate(e);
			}
		}
	}


	public static class MethodHandlesInstanciator implements Instanciator {
		private Map<Class<?>, MethodHandle> handles = Maps.newHashMap();
		
		@Override
		public <T> T instanciate(Class<T> clazz, NetworkContext nc) {
			try {
				if (!handles.containsKey(clazz)) {
					Constructor<T> cons = clazz.getConstructor(NetworkContext.class);
					handles.put(clazz, MethodHandles.lookup().unreflectConstructor(cons));
				}
				return (T)handles.get(clazz).invoke(nc);
			} catch (Throwable e) {
				throw Throwables.propagate(e);
			}
		}

	}


	public interface Instanciator {
		<T> T instanciate(Class<T> clazz, NetworkContext nc);
	}
}
