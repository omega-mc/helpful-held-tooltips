package draylar.helpfulheldtooltips.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.EnchantedBookItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

@Environment(EnvType.CLIENT)
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {

    @Shadow
    @Final
    private MinecraftClient client;

    @Shadow
    private int heldItemTooltipFade;

    @Shadow
    private ItemStack currentStack;

    @Shadow
    private int scaledWidth;

    @Shadow
    private int scaledHeight;

    @Shadow public abstract TextRenderer getTextRenderer();

    /**
     * @author Draylar
     */
    @Overwrite
    public void renderHeldItemTooltip(MatrixStack matrixStack) {
        this.client.getProfiler().push("selectedItemName");

        if(heldItemTooltipFade > 0 && !currentStack.isEmpty()) {
            MutableText text = Text.literal("").append(currentStack.getName()).formatted(currentStack.getRarity().formatting);

            // italicize if stack has a custom name
            if(currentStack.hasCustomName()) {
                text.formatted(Formatting.ITALIC);
            }

            // get enchantments from stack
            Map<Enchantment, Integer> enchantments = new HashMap<>();
            if(currentStack.hasEnchantments()) {
                enchantments = EnchantmentHelper.get(currentStack);
            }

            NbtList storedEnchantments = EnchantedBookItem.getEnchantmentNbt(currentStack);
            enchantments.putAll(EnchantmentHelper.fromNbt(storedEnchantments));

            // get positioning information
            int x = (scaledWidth - getTextRenderer().getWidth(text)) / 2;
            int bottomOffset = 59;
            int enchantmentOffset = enchantments.size() * 12;
            int y = scaledHeight - bottomOffset - enchantmentOffset;
            if(!client.interactionManager.hasStatusBars()) {
                y += 14;
            }

            // get opacity information
            int k = (int) ((float) heldItemTooltipFade * 256.0F / 10.0F);
            if(k > 255) {
                k = 255;
            }

            // render the tooltip if the opacity is over 0
            if(k > 0) {
                // start gl
                matrixStack.push();
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();

                // positioning information
                int var10000 = x - 2;
                int var10001 = y - 2;
                int var10002 = x + this.getTextRenderer().getWidth(text) + 2;

                // render tooltip
                DrawableHelper.fill(matrixStack, var10000, var10001, var10002, y + 9 + 2, this.client.options.getTextBackgroundColor(0));
                this.getTextRenderer().drawWithShadow(matrixStack, text, (float) x, (float) y, 16777215 + (k << 24));

                // draw enchantments
                int count = 1;
                for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
                    Enchantment enchantment = entry.getKey();
                    Integer level = entry.getValue();

                    Text enchantmentText = Text.translatable(enchantment.getTranslationKey()).append(" ").append(Text.translatable("potion.potency." + (level - 1))).formatted(Formatting.GRAY);
                    x = (this.scaledWidth - getTextRenderer().getWidth(enchantmentText)) / 2;
                    getTextRenderer().drawWithShadow(matrixStack, enchantmentText, (float) x, (float) y + 12 * count, 16777215 + (k << 24));

                    count++;
                }

                // end gl
                RenderSystem.disableBlend();
                matrixStack.pop();
            }
        }

        client.getProfiler().pop();
    }

    /**
     * In vanilla, the item name tooltip does not show when switching between items, if the second item
     *
     * <p>
     * - is the same type
     * - has the same name
     * - is not empty
     *
     * <p>
     * This has the side effect of disabling the tooltip from showing when you switch to a weapon of the same type with different enchantments.
     * We fix this by adding a single check for enchantment equality when decrementing the held item tooltip fade.
     */
    @Inject(
            method = "tick()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerInventory;getMainHandStack()Lnet/minecraft/item/ItemStack;"),
            cancellable = true
    )
    private void adjustFade(CallbackInfo ci) {
        ItemStack itemStack = this.client.player.getInventory().getMainHandStack();

        // stack is empty, set fade to 100% transparent
        if(itemStack.isEmpty()) {
            heldItemTooltipFade = 0;
        }

        // currentStack is not empty, held stack item is same as current item, names match
        // addition is also checking that enchantments match
        else if(!currentStack.isEmpty() && itemStack.getItem() == currentStack.getItem() && itemStack.getName().equals(currentStack.getName()) && itemStack.getEnchantments().equals(this.currentStack.getEnchantments())) {
            if(heldItemTooltipFade > 0) {
                --heldItemTooltipFade;
            }
        }

        // new item, reset fade to 40 (2 seconds)
        else {
            heldItemTooltipFade = 40;
        }

        currentStack = itemStack;
        ci.cancel();
    }
}
