package org.vivecraft.client_vr.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.vivecraft.client.gui.framework.screens.TwoHandedScreen;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.MethodHolder;
import org.vivecraft.client_vr.gameplay.screenhandlers.GuiHandler;
import org.vivecraft.client_vr.gameplay.screenhandlers.RadialHandler;
import org.vivecraft.client_vr.provider.ControllerType;
import org.vivecraft.client_vr.provider.MCVR;
import org.vivecraft.client_vr.provider.openvr_lwjgl.VRInputAction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GuiRadial extends TwoHandedScreen {
    // size and spacing of the hotbar grid buttons, at a grid size of 1
    private static final int GRID_BUTTON_SIZE = 40;
    private static final int GRID_SPACING = 46;

    private boolean isShift = false;
    String[] arr;
    private final List<RadialHotbarButton> hotbarButtons = new ArrayList<>();
    // last valid aimed grid button per controller, indexed by ControllerType ordinal
    private final RadialHotbarButton[] hotbarAim = new RadialHotbarButton[ControllerType.values().length];
    // aiming closer than this to the center selects the center slot, set from the grid size
    private float gridDeadzone = GRID_SPACING * 0.5F;
    // normal radial buttons that are bound to a hotbar slot, and their slot
    private final Map<GuiEventListener, Integer> hotbarSlotButtons = new HashMap<>();
    // targets shown last frame, to send a haptic pulse when they change
    private RadialHotbarButton shownMainTarget;
    private RadialHotbarButton shownOffhandTarget;

    @Override
    public void init() {
        this.arr = this.dh.vrSettings.vrRadialItems;
        String[] altSet = this.dh.vrSettings.vrRadialItemsAlt;
        this.clearWidgets();

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.hotbarButtons.clear();
        this.hotbarSlotButtons.clear();
        Arrays.fill(this.hotbarAim, null);
        this.shownMainTarget = null;
        this.shownOffhandTarget = null;

        // primary set as a hotbar icon grid, the alt set stays a normal radial, the offhand radial is always a grid
        // spectators use the hotbar for the spectator menu, so they get the normal radial
        boolean grid = RadialHandler.isOffhandRadial() || (this.dh.vrSettings.radialHotbarGrid && !this.isShift);
        if (grid && this.minecraft.player != null && !this.minecraft.player.isSpectator()) {
            this.initHotbarGrid(centerX, centerY);
            return;
        }

        if (this.isShift) {
            this.arr = altSet;
        }

        for (int i = 0; i < this.dh.vrSettings.vrRadialButtons; i++) {
            // not all buttons need to be set
            if (i >= this.arr.length) break;

            String current = this.arr[i];
            int index = i;
            Arrays.stream(this.minecraft.options.keyMappings)
                .filter(keymapping -> keymapping.getName().equalsIgnoreCase(current))
                .findFirst()
                .ifPresent(keymapping -> {
                    VRInputAction vrinputaction = MCVR.get().getInputAction(this.arr[index]);
                    String label = I18n.get(keymapping.getName());
                    int hotbarSlot = getHotbarSlot(keymapping.getName());
                    // show the item in hotbar slot keybinds instead of "Hotbar Slot N"
                    if (this.dh.vrSettings.radialShowItems && hotbarSlot >= 0 && this.minecraft.player != null) {
                        ItemStack stack = this.minecraft.player.getInventory().getItem(hotbarSlot);
                        label = (hotbarSlot + 1) + ": " + (stack.isEmpty() ? I18n.get("vivecraft.gui.radial.empty") :
                            stack.getHoverName().getString() +
                                (stack.getCount() > 1 ? " x" + stack.getCount() : ""));
                    }
                    if (vrinputaction != null &&
                        (vrinputaction.keyBinding.isDown() || MethodHolder.isKeyDown(vrinputaction.keyBinding.key)))
                    {
                        label = "§a" + label;
                    }
                    Button button = createButton(label, (p) -> {
                        if (vrinputaction != null) {
                            if (vrinputaction.keyBinding.isDown() ||
                                MethodHolder.isKeyDown(vrinputaction.keyBinding.key))
                            {
                                vrinputaction.stopHoldingBinding(2);
                            } else {
                                vrinputaction.holdBinding();
                            }
                        }
                    }, index, centerX, centerY);
                    this.addRenderableWidget(button);
                    if (hotbarSlot >= 0) {
                        this.hotbarSlotButtons.put(button, hotbarSlot);
                    }
                });
        }
    }

    /**
     * lays out the 9 hotbar slots as a 3x3 grid, the held slot in the center,
     * the other 8 slots around it in hotbar order, starting top left
     */
    private void initHotbarGrid(int centerX, int centerY) {
        float scale = this.dh.vrSettings.radialHotbarGridScale;
        int size = Math.round(GRID_BUTTON_SIZE * scale);
        int spacing = Math.round(GRID_SPACING * scale);
        this.gridDeadzone = spacing * 0.5F;

        int held = this.minecraft.player.getInventory().getSelectedSlot();
        int nextSlot = 0;
        // buttons are added in grid position order, the direction lookup relies on that
        for (int position = 0; position < 9; position++) {
            int slot;
            if (position == 4) {
                slot = held;
            } else {
                if (nextSlot == held) {
                    nextSlot++;
                }
                slot = nextSlot++;
            }
            int col = position % 3 - 1;
            int row = position / 3 - 1;
            int selectSlot = slot;
            RadialHotbarButton button = new RadialHotbarButton(slot,
                centerX + col * spacing - size / 2,
                centerY + row * spacing - size / 2,
                size, (p) -> this.selectHotbarSlot(selectSlot));
            this.hotbarButtons.add(button);
            this.addRenderableWidget(button);
        }
    }

    /**
     * @return if the radial is currently showing the hotbar grid
     */
    public boolean isHotbarGrid() {
        return !this.hotbarButtons.isEmpty();
    }

    /**
     * gets the hotbar grid button in the direction of the given screen position, as seen from the center.
     * close to the center selects the center button, otherwise the nearest of the 8 directions
     *
     * @param x screen x coordinate
     * @param y screen y coordinate
     * @return button in that direction, or null if the grid is not shown
     */
    public RadialHotbarButton getHotbarButtonInDirection(double x, double y) {
        if (this.hotbarButtons.isEmpty()) return null;

        double dx = x - this.width * 0.5;
        double dy = y - this.height * 0.5;
        int col = 0;
        int row = 0;
        if (dx * dx + dy * dy > this.gridDeadzone * this.gridDeadzone) {
            // snap the angle to one of 8 directions, screen y points down
            int sector = (int) Math.round(Math.atan2(dy, dx) / (Math.PI * 0.25));
            col = (int) Math.round(Math.cos(sector * Math.PI * 0.25));
            row = (int) Math.round(Math.sin(sector * Math.PI * 0.25));
        }
        return this.hotbarButtons.get((row + 1) * 3 + (col + 1));
    }

    /**
     * updates the aimed grid button of the given controller from its cursor.
     * if the cursor left the radial, the last aimed button is kept, so overshooting a direction still works
     *
     * @param controller controller to update
     * @return the aimed button, or null if that controller never aimed at the radial
     */
    private RadialHotbarButton updateHotbarAim(ControllerType controller) {
        boolean left = controller == ControllerType.LEFT;
        float cursorX = left ? this.cursorX1 : this.cursorX2;
        float cursorY = left ? this.cursorY1 : this.cursorY2;
        if (cursorX >= 0.0F && cursorY >= 0.0F) {
            this.hotbarAim[controller.ordinal()] = this.getHotbarButtonInDirection(
                cursorX * this.width / GuiHandler.GUI_WIDTH,
                cursorY * this.height / GuiHandler.GUI_HEIGHT);
        }
        return this.hotbarAim[controller.ordinal()];
    }

    /**
     * selects the hotbar slot the given controller last aimed at, as the main hand item
     *
     * @param controller controller that made the selection
     */
    public void selectAimedHotbarSlot(ControllerType controller) {
        RadialHotbarButton button = this.updateHotbarAim(controller);
        if (button != null && this.minecraft.player != null) {
            this.minecraft.player.getInventory().setSelectedSlot(button.getSlot());
            // the held item moved, rebuild the grid in case the radial stays open
            this.reinit = true;
        }
    }

    /**
     * called when the offhand radial button is released, moves the hovered item to the offhand.
     * the center cancels, so releasing without aiming doesn't change anything
     */
    public void releaseOffhandRadial() {
        RadialHotbarButton button = this.updateHotbarAim(ControllerType.LEFT);
        if (button != null && !this.isCenter(button)) {
            this.swapHotbarSlotToOffhand(button.getSlot());
        }
    }

    private boolean isCenter(RadialHotbarButton button) {
        return this.hotbarButtons.size() == 9 && this.hotbarButtons.get(4) == button;
    }

    /**
     * moves the item of the hotbar slot the given controller last aimed at into the offhand,
     * the previous offhand item goes into that hotbar slot. the held main hand slot doesn't change
     *
     * @param controller controller that made the selection
     */
    public void swapAimedHotbarSlotToOffhand(ControllerType controller) {
        RadialHotbarButton button = this.updateHotbarAim(controller);
        if (button != null) {
            this.swapHotbarSlotToOffhand(button.getSlot());
        }
    }

    /**
     * @param x screen x coordinate
     * @param y screen y coordinate
     * @return hotbar slot of the normal radial button at that position, or -1 if there is none
     */
    public int getHotbarSlotAt(double x, double y) {
        return this.getChildAt(x, y).map(widget -> this.hotbarSlotButtons.getOrDefault(widget, -1)).orElse(-1);
    }

    /**
     * moves the item of the given hotbar slot into the offhand, the previous offhand item goes into that slot.
     * the held main hand slot doesn't change
     *
     * @param slot hotbar slot, 0-8
     */
    public void swapHotbarSlotToOffhand(int slot) {
        LocalPlayer player = this.minecraft.player;
        if (player == null || player.isSpectator() || slot < 0 || slot > 8) return;

        // the server swaps the held slot with the offhand, so briefly hold the aimed slot,
        // same packets as pressing the hotbar key and then the swap offhand key
        Inventory inventory = player.getInventory();
        int previous = inventory.getSelectedSlot();
        if (slot != previous) {
            inventory.setSelectedSlot(slot);
            player.connection.send(new ServerboundSetCarriedItemPacket(slot));
        }
        player.connection.send(
            new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                BlockPos.ZERO, Direction.DOWN));
        if (slot != previous) {
            inventory.setSelectedSlot(previous);
            player.connection.send(new ServerboundSetCarriedItemPacket(previous));
        }
    }

    /**
     * @param keyName KeyMapping name
     * @return hotbar slot 0-8 of a hotbar slot KeyMapping, or -1 if it isn't one
     */
    private static int getHotbarSlot(String keyName) {
        if (!keyName.startsWith("key.hotbar.")) return -1;
        try {
            int slot = Integer.parseInt(keyName.substring("key.hotbar.".length())) - 1;
            return slot >= 0 && slot <= 8 ? slot : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private void selectHotbarSlot(int slot) {
        if (this.minecraft.player != null) {
            this.minecraft.player.getInventory().setSelectedSlot(slot);
        }
    }

    public void setShift(boolean shift) {
        if (shift != this.isShift) {
            this.isShift = shift;
            this.init();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.extractBackground(graphics, mouseX, mouseY, partialTick);

        if (this.isHotbarGrid()) {
            RadialHotbarButton mainTarget;
            RadialHotbarButton offhandTarget;
            if (RadialHandler.isOffhandRadial()) {
                // offhand radial: only the offhand aims, the center cancels
                mainTarget = null;
                offhandTarget = this.updateHotbarAim(ControllerType.LEFT);
                if (offhandTarget != null && this.isCenter(offhandTarget)) {
                    offhandTarget = null;
                }
            } else {
                // main hand highlight follows the controller that opened the radial,
                // offhand highlight follows the offhand controller while it points at the radial
                ControllerType active = RadialHandler.getActiveController();
                mainTarget = this.updateHotbarAim(active != null ? active : ControllerType.RIGHT);
                offhandTarget = RadialHandler.isUsingController(ControllerType.LEFT) ?
                    this.updateHotbarAim(ControllerType.LEFT) : null;
            }

            for (RadialHotbarButton button : this.hotbarButtons) {
                button.selected = button == mainTarget;
                button.offhandSelected = button == offhandTarget;
            }

            if (mainTarget != this.shownMainTarget) {
                this.shownMainTarget = mainTarget;
                ControllerType active = RadialHandler.getActiveController();
                MCVR.get().triggerHapticPulse(active != null ? active : ControllerType.RIGHT, 300);
            }
            if (offhandTarget != this.shownOffhandTarget) {
                this.shownOffhandTarget = offhandTarget;
                if (offhandTarget != null) {
                    MCVR.get().triggerHapticPulse(ControllerType.LEFT, 300);
                }
            }
        }

        super.extractRenderState(graphics, 0, 0, partialTick);
    }

    /**
     * creates a Button withte given {@code action} and positions it in the radial circle
     *
     * @param label   label of the button
     * @param action  action to call when pressing the button
     * @param index   button index in the circle
     * @param centerX center of the circle on the screen, x coordinate
     * @param centerY center of the circle on the screen, y coordinate
     * @return Button positioned athte right spot
     */
    public static Button createButton(
        String label, Button.OnPress action, int index, int centerX, int centerY)
    {
        int buttonWidthMin = 120;
        int numButtons = ClientDataHolderVR.getInstance().vrSettings.vrRadialButtons;

        // distance from the center, with 14 buttons, move them closer together
        float dist = numButtons * (numButtons >= 14 ? 5F : 5.5F);

        int buttonWidth = Math.max(buttonWidthMin, Minecraft.getInstance().font.width(label) + 4);
        // coords of the button, button 0 is at the top with x = 0, y = -dist
        float distX = numButtons * 4 + buttonWidth * 0.5F;

        // position buttons on equal y spacing
        float btnIndex = (index < numButtons / 2 ? index : numButtons - index) / (float) (numButtons / 2);
        int y = (int) (2.0F * dist * btnIndex - dist);

        // position x so the buttons produce an ellipse
        int x = (int) (distX * (Math.sqrt(1.0F - (y * y) / (dist * dist))));

        // move in between buttons closer to the middle
        if (Math.abs(y) > 20) {
            x = (int) (x * 0.87F);
        }

        // don't go over the center
        if (index != 0 && index != numButtons / 2) {
            x = Math.max(buttonWidth / 2, x);
        }

        // second half of buttons should be on the left side
        x *= index > numButtons / 2 ? -1 : 1;

        return new Button.Builder(Component.translatable(label), action)
            .size(buttonWidth, 20)
            .pos(centerX + x - buttonWidth / 2, centerY + y - 10)
            .build();
    }
}
