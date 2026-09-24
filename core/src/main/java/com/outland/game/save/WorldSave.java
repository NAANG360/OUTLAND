package com.outland.game.save;

import com.outland.game.player.PlayerState;
import com.outland.game.world.*;
import java.io.*;

/** Versioned binary save format; deliberately independent of renderer/GPU objects. */
public final class WorldSave {
    private static final int MAGIC=0x4F55544C; // OUTL
    private static final int VERSION=1;
    private static final int MAX_BLOCKS=2_000_000;
    public static final class Snapshot {
        public final World world;
        public final PlayerState player;
        public Snapshot(World world,PlayerState player){this.world=world;this.player=player;}
    }
    private WorldSave() {}

    public static void write(OutputStream output,World world,PlayerState player,long seed) throws IOException {
        DataOutputStream out=new DataOutputStream(new BufferedOutputStream(output));
        out.writeInt(MAGIC);out.writeInt(VERSION);out.writeLong(seed);out.writeInt(world.size());
        for(World.Block block:world.snapshot()) {out.writeInt(block.x);out.writeInt(block.y);out.writeInt(block.z);out.writeByte(block.type.id());}
        out.writeFloat(player.position.x);out.writeFloat(player.position.y);out.writeFloat(player.position.z);
        out.writeFloat(player.yaw);out.writeFloat(player.pitch);out.writeFloat(player.verticalVelocity);
        out.writeBoolean(player.grounded);out.writeInt(player.health);out.writeInt(player.selectedBlock);
        for(int count:player.inventory)out.writeInt(count);
        out.flush();
    }

    public static Snapshot read(InputStream input) throws IOException {
        DataInputStream in=new DataInputStream(new BufferedInputStream(input));
        if(in.readInt()!=MAGIC)throw new IOException("Not an OUTLAND save");
        int version=in.readInt();if(version!=VERSION)throw new IOException("Unsupported save version: "+version);
        long seed=in.readLong();int count=in.readInt();
        if(count<0||count>MAX_BLOCKS)throw new IOException("Invalid block count: "+count);
        World world=new World(seed);
        try {
            for(int i=0;i<count;i++) {
                int x=in.readInt(),y=in.readInt(),z=in.readInt();BlockType type=BlockType.fromId(in.readUnsignedByte());
                if(!world.setBlock(x,y,z,type))throw new IOException("Duplicate block coordinate");
            }
            PlayerState player=new PlayerState();
            player.position.set(in.readFloat(),in.readFloat(),in.readFloat());
            player.yaw=in.readFloat();player.pitch=in.readFloat();player.verticalVelocity=in.readFloat();
            player.grounded=in.readBoolean();player.health=in.readInt();player.selectedBlock=in.readInt();
            for(int i=0;i<player.inventory.length;i++){player.inventory[i]=in.readInt();if(player.inventory[i]<0)throw new IOException("Negative inventory count");}
            if(player.health<0||player.selectedBlock<0||player.selectedBlock>=BlockType.values().length)throw new IOException("Invalid player state");
            if(!Float.isFinite(player.position.x)||!Float.isFinite(player.position.y)||!Float.isFinite(player.position.z)||!Float.isFinite(player.yaw)||!Float.isFinite(player.pitch)||!Float.isFinite(player.verticalVelocity))throw new IOException("Non-finite player state");
            return new Snapshot(world,player);
        } catch(IllegalArgumentException invalid){throw new IOException("Invalid save data",invalid);}
    }
}
