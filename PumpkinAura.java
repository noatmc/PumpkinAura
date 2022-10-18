package me.alpha432.oyvey.features.modules.combat;

import me.alpha432.oyvey.OyVey;
import me.alpha432.oyvey.event.events.PacketEvent;
import me.alpha432.oyvey.event.events.Render3DEvent;
import me.alpha432.oyvey.event.events.TotemPopEvent;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.modules.client.Colors;
import me.alpha432.oyvey.features.setting.Setting;
import me.alpha432.oyvey.util.*;
import net.minecraft.block.BlockPumpkin;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock;
import net.minecraft.network.play.server.SPacketExplosion;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class PumpkinAura extends Module {
    public static Setting<Float>   placeRange       = new Setting<>("Place Range", 6f, 0.1f, 6f);
    public static Setting<Boolean> sequential       = new Setting<>("Sequential", true);
    public static Setting<Integer> minDamage        = new Setting<>("Min Damage", 6, 0, 36);
    public static Setting<Integer> maxDamage        = new Setting<>("Max Damage", 6, 0, 36);
    public static Setting<Boolean> multiPlace       = new Setting<>("Multiplace", true);
    public static Setting<Boolean> antiTotem        = new Setting<>("AntiTotem", false);
    public static Setting<Boolean> autoSwitch       = new Setting<>("AutoSwitch", false);
    public static Setting<Boolean> silent           = new Setting<>("Silent", false);
    public static Setting<Boolean> render           = new Setting<>("Render", true);


    BlockPos renderPos, lastPos, lastRenderPos  = null;
    AxisAlignedBB renderBB                      = null;
    float timePassed                            = 0f;
    Timer timer                                 = new Timer();
    HashMap<EntityPlayer, Long> lastPops        = new HashMap<>();

    public PumpkinAura() {
        super("PumpkinAura", "AutoCrystal+", Category.COMBAT);
        register(
                placeRange,
                sequential,
                minDamage,
                maxDamage,
                multiPlace,
                antiTotem,
                autoSwitch,
                silent,
                render
        );
    }

    public boolean isDoublePoppable(EntityPlayer player, float damage) {
        if (antiTotem.getValue()) {
            if (lastPops.get(player) == null) return true;
            return System.currentTimeMillis() - lastPops.get(player) > 500 && EntityUtil.getHealth(player, true) - damage <= 0f;
        }
        return !antiTotem.getValue();
    }

    public void onEnable() {
        timer           = new Timer();
        renderPos       = null;
        lastPos         = null;
        lastRenderPos   = null;
        renderBB        = null;
        timePassed      = 0f;
        lastPops        = new HashMap<>();
    }

    @SubscribeEvent
    public void onTotemPop(TotemPopEvent event) {
        lastPops.put(event.getEntity(), System.currentTimeMillis());
    }

    @SubscribeEvent
    public void onPacketReceive(PacketEvent.Receive event) {
        if (event.getPacket() instanceof SPacketExplosion && sequential.getValue()) {
            BlockPos packet = new BlockPos(((SPacketExplosion) event.getPacket()).posX, ((SPacketExplosion) event.getPacket()).posY, ((SPacketExplosion) event.getPacket()).posZ);
            if (packet.equals(lastPos)) {
                handlePlacing(packet, true);
            }
        }
    }

    @Override
    public void onTick() {
        placePumpkin();
    }

    public void placePumpkin() {
        List<EntityPlayer> players = mc.world.playerEntities.stream().filter(
                player -> !player.equals(mc.player) && player.getDistance(mc.player) <= 12f && !(player.isDead || player.getHealth() <= 0) && !OyVey.friendManager.isFriend(player.getName())
        ).collect(Collectors.toList());
        BlockPos placePos = null;
        float idk = 0.5f;
        for (EntityPlayer player : players) {
//            Command.sendMessage("wtf");
            for (BlockPos pos : CrystalUtil.possiblePlacePositions(placeRange.getValue(), true, false, false)) {
                float damage = CrystalUtil.pumpkinWtf(pos.getX(), pos.getY() + 1, pos.getZ(), player);
                float selfDamage = CrystalUtil.pumpkinWtf(pos.getX(), pos.getY() + 1, pos.getZ(), mc.player);
                if (damage > minDamage.getValue() && damage > idk && BlockUtil.canPlacePumpkin(pos) && isDoublePoppable(player, damage) && selfDamage < maxDamage.getValue()) {
                    placePos = pos;
                    idk = damage;
                    if (multiPlace.getValue()) {
                        handlePlacing(pos, false);
                    }
                }
            }
        }
        renderPos = placePos;
        if (placePos != null) {
            handlePlacing(placePos, false);
        }
    }

    public void handlePlacing(BlockPos pos, boolean sPacketExplosion) {
        int slot = mc.player.inventory.currentItem;
        if (autoSwitch.getValue()) InventoryUtil.switchToHotbarSlot(BlockPumpkin.class, silent.getValue());
        EnumHand hand = InventoryUtil.getHandHolding(Item.getItemFromBlock(Blocks.PUMPKIN));
        if (hand != null) {
            BlockUtil.mc.player.connection.sendPacket(new CPacketPlayerTryUseItemOnBlock(pos, EnumFacing.UP, hand, 0.0f, 0.0f, 0.0f));
            renderPos = pos;
            if (!sPacketExplosion) {
                lastPos = pos;
            }
            if (silent.getValue()) InventoryUtil.switchToHotbarSlot(slot, false);
            mc.player.swingArm(hand);
        }
    }

    @SubscribeEvent
    public void onRender3D(Render3DEvent event) {
        if (fullNullCheck()) return;
        if (render.getValue() && renderPos != null) {
            if (this.lastRenderPos == null || AutoCrystal.mc.player.getDistance(this.renderBB.minX, this.renderBB.minY, this.renderBB.minZ) > 12.0) {
                this.lastRenderPos = this.renderPos;
                this.renderBB = new AxisAlignedBB(this.renderPos);
                this.timePassed = 0.0f;
            }
            if (!this.lastRenderPos.equals(this.renderPos)) {
                this.lastRenderPos = this.renderPos;
                this.timePassed = 0.0f;
            }
            double xDiff = (double)this.renderPos.getX() - this.renderBB.minX;
            double yDiff = (double)this.renderPos.getY() - this.renderBB.minY;
            double zDiff = (double)this.renderPos.getZ() - this.renderBB.minZ;
            float multiplier = this.timePassed / 900f * 0.8f;
            if (multiplier > 1.0f) {
                multiplier = 1.0f;
            }
            this.renderBB = this.renderBB.offset(xDiff * (double)multiplier, yDiff * (double)multiplier, zDiff * (double)multiplier);
            RenderUtil.drawSexyBoxPhobosIsRetardedFuckYouESP(this.renderBB, Colors.INSTANCE.getCurrentColor(), Colors.INSTANCE.getCurrentColor(), 1, true, true, 1.0f, 1.0f, 1.0f);
            this.timePassed = this.renderBB.equals(new AxisAlignedBB(this.renderPos)) ? 0.0f : this.timePassed + 50.0f;
        }
    }
}
