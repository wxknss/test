package thunder.hack.mixin;

import net.minecraft.client.gui.hud.ChatHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import thunder.hack.features.modules.misc.ChatUtils;

@Mixin(ChatHud.class)
public class ChatHudMixin {
    @Inject(method = "clear", at = @At("HEAD"), cancellable = true)
    private void onClear(CallbackInfo ci) {
        if (ChatUtils.isAntiClearEnabled()) {
            ci.cancel();
        }
    }
}
