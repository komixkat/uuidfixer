package dev.komixkat.uuidfixer.mixin;

import dev.komixkat.uuidfixer.UuidLookupService;
import net.minecraft.core.UUIDUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(UUIDUtil.class)
abstract class UUIDUtilMixin {

	@Inject(method = "createOfflinePlayerUUID", at = @At("HEAD"), cancellable = true)
	private static void uuidfixer$preferRealUuid(String username, CallbackInfoReturnable<UUID> cir) {
		UUID realUuid = UuidLookupService.lookup(username);
		if (realUuid != null) {
			cir.setReturnValue(realUuid);
		}
	}
}
