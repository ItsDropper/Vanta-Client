package name.vantaclient.mixin;

import name.vantaclient.client.feature.CrashPatchFeature;
import net.minecraft.SystemReport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.resources.language.LanguageManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds opt-in Vanta diagnostics to the system section of local crash reports. */
@Mixin(Minecraft.class)
public abstract class MinecraftCrashReportMixin {
    @Inject(method = "fillSystemReport", at = @At("TAIL"))
    private static void vanta$appendCrashDiagnostics(
            SystemReport report,
            Minecraft minecraft,
            LanguageManager languageManager,
            String launchVersion,
            Options options,
            CallbackInfoReturnable<SystemReport> cir
    ) {
        if (!CrashPatchFeature.INSTANCE.isEnabled()) return;
        CrashPatchFeature.collectDiagnostics().forEach(
                (name, value) -> report.setDetail(name, value));
    }
}
