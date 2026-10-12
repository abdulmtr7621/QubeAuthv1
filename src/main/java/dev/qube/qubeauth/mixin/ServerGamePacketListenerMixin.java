package dev.qube.qubeauth.mixin;

import dev.qube.qubeauth.Qubeauth;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerMixin {
    @Inject(method = "performChatCommand", at = @At("HEAD"), cancellable = true, require = 0)
    private void qubeauth$blockCommandsBeforeLogin(String command, CallbackInfo ci) {
        ServerGamePacketListenerImpl self = (ServerGamePacketListenerImpl)(Object)this;
        if (Qubeauth.cancelCommand(self.player)) ci.cancel();
    }
}
