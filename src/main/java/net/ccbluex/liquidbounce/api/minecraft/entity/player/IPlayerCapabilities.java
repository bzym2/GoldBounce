package net.ccbluex.liquidbounce.api.minecraft.entity.player;

import kotlin.Metadata;

/* compiled from: IPlayerCapabilities.kt */
@Metadata(mv = {1, 1, 16}, bv = {1, 0, 3}, k = 1, d1 = {"��\u0012\n\u0002\u0018\u0002\n\u0002\u0010��\n��\n\u0002\u0010\u000b\n\u0002\b\u0007\bf\u0018��2\u00020\u0001R\u0012\u0010\u0002\u001a\u00020\u0003X¦\u0004¢\u0006\u0006\u001a\u0004\b\u0004\u0010\u0005R\u0012\u0010\u0006\u001a\u00020\u0003X¦\u0004¢\u0006\u0006\u001a\u0004\b\u0006\u0010\u0005R\u0018\u0010\u0007\u001a\u00020\u0003X¦\u000e¢\u0006\f\u001a\u0004\b\u0007\u0010\u0005\"\u0004\b\b\u0010\t¨\u0006\n"}, d2 = {"Lnet/ccbluex/liquidbounce/api/minecraft/entity/player/IPlayerCapabilities;", "", "allowFlying", "", "getAllowFlying", "()Z", "isCreativeMode", "isFlying", "setFlying", "(Z)V", "Pride"})
/* loaded from: Ordinary２－９ｃｒｋ.jar:net/ccbluex/liquidbounce/api/minecraft/entity/player/IPlayerCapabilities.class */
public interface IPlayerCapabilities {
    boolean getAllowFlying();

    boolean isFlying();

    void setFlying(boolean z);

    boolean isCreativeMode();
}
