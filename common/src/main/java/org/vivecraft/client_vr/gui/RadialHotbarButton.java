package org.vivecraft.client_vr.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Square radial button that shows the item icon of a hotbar slot
 */
public class RadialHotbarButton extends Button {

    // copied over from AbstractButton
    private static final WidgetSprites SPRITES = new WidgetSprites(
        Identifier.withDefaultNamespace("widget/button"),
        Identifier.withDefaultNamespace("widget/button_disabled"),
        Identifier.withDefaultNamespace("widget/button_highlighted"));

    private final int slot;
    // item icons are 16x16, scaled to fill the button
    private final float itemScale;

    /**
     * if this button is the one currently aimed at
     */
    public boolean selected;

    /**
     * if this button is the one the offhand is aiming at
     */
    public boolean offhandSelected;

    public RadialHotbarButton(int slot, int x, int y, int size, OnPress onPress) {
        super(x, y, size, size, Component.empty(), onPress, Button.DEFAULT_NARRATION);
        this.slot = slot;
        this.itemScale = size / 20.0F;
    }

    public int getSlot() {
        return this.slot;
    }

    /**
     * selection is done by direction in GuiRadial, not by hovering.
     * this also stops the screen from sending a hover haptic pulse on top of the direction change pulse
     */
    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return false;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();

        graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
            SPRITES.get(this.active, this.selected || this.offhandSelected),
            this.getX(), this.getY(), this.getWidth(), this.getHeight());

        if (mc.player != null) {
            Inventory inventory = mc.player.getInventory();

            // mark the currently held slot
            if (inventory.getSelectedSlot() == this.slot) {
                graphics.outline(this.getX(), this.getY(), this.getWidth(), this.getHeight(), 0xFFFFAA00);
            }

            ItemStack stack = inventory.getItem(this.slot);
            if (!stack.isEmpty()) {
                graphics.pose().pushMatrix();
                graphics.pose().translate(this.getX() + this.getWidth() * 0.5F,
                    this.getY() + this.getHeight() * 0.5F);
                graphics.pose().scale(this.itemScale, this.itemScale);
                graphics.fakeItem(stack, -8, -8);
                graphics.itemDecorations(mc.font, stack, -8, -8);
                graphics.pose().popMatrix();
            }
        }

        // mark the aimed slots, inset so they don't cover the held slot marker or each other
        if (this.selected) {
            graphics.outline(this.getX() + 2, this.getY() + 2, this.getWidth() - 4, this.getHeight() - 4,
                0xFFFFFFFF);
        }
        if (this.offhandSelected) {
            graphics.outline(this.getX() + 4, this.getY() + 4, this.getWidth() - 8, this.getHeight() - 8,
                0xFF55FFFF);
        }
    }
}
