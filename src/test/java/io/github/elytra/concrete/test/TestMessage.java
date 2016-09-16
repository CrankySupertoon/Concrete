package io.github.elytra.concrete.test;

import io.github.elytra.concrete.Message;
import io.github.elytra.concrete.NetworkContext;
import io.github.elytra.concrete.annotation.field.MarshalledAs;
import io.github.elytra.concrete.annotation.type.Asynchronous;
import io.github.elytra.concrete.annotation.type.ReceivedOn;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;

@ReceivedOn(Side.SERVER)
@Asynchronous
public class TestMessage extends Message {

	public boolean true1 = true;
	public boolean flse2 = false;
	public boolean flse3 = false;
	public boolean true4 = true;
	public boolean true5 = true;
	public boolean flse6 = false;
	public boolean true7 = true;
	public boolean true8 = true;
	public boolean flse9 = false;
	public boolean true10 = true;
	
	@MarshalledAs("u8")
	public int someByte = 255;
	@MarshalledAs("i8")
	public int someSignedByte = -128;
	public String someString = "Foo bar";
	
	public TestMessage(NetworkContext ctx) {
		super(ctx);
	}

	@Override
	protected void handle(EntityPlayer sender) {
		System.out.println(sender);
		System.out.println(this);
	}

	@Override
	public String toString() {
		return "TestMessage [true1=" + true1 + ", flse2=" + flse2 + ", flse3="
				+ flse3 + ", true4=" + true4 + ", true5=" + true5 + ", flse6="
				+ flse6 + ", true7=" + true7 + ", true8=" + true8 + ", flse9="
				+ flse9 + ", true10=" + true10 + ", someByte=" + someByte
				+ ", someSignedByte=" + someSignedByte + ", someString="
				+ someString + "]";
	}
	
	
	
	

}
