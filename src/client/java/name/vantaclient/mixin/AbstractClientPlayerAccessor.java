package name.vantaclient.mixin;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Supplies the title-scene avatar with profile-backed skin data. */
@Mixin(AbstractClientPlayer.class)
public interface AbstractClientPlayerAccessor {
    @Accessor("playerInfo")
    void vanta$setPlayerInfo(PlayerInfo playerInfo);
}
