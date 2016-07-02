/*
 * This file is part of LanternServer, licensed under the MIT License (MIT).
 *
 * Copyright (c) LanternPowered <https://github.com/LanternPowered>
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) Contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the Software), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED AS IS, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.lanternpowered.server.inventory;

import org.lanternpowered.server.entity.living.player.LanternPlayer;
import org.lanternpowered.server.game.Lantern;
import org.lanternpowered.server.inventory.entity.HumanInventoryView;
import org.lanternpowered.server.inventory.entity.HumanMainInventory;
import org.lanternpowered.server.inventory.entity.LanternHotbar;
import org.lanternpowered.server.inventory.slot.LanternSlot;
import org.lanternpowered.server.network.vanilla.message.type.play.MessagePlayInClickWindow;
import org.lanternpowered.server.network.vanilla.message.type.play.MessagePlayInCreativeWindowAction;
import org.lanternpowered.server.network.vanilla.message.type.play.MessagePlayInOutCloseWindow;
import org.lanternpowered.server.network.vanilla.message.type.play.MessagePlayOutSetWindowSlot;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.data.Transaction;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.living.player.gamemode.GameMode;
import org.spongepowered.api.entity.living.player.gamemode.GameModes;
import org.spongepowered.api.event.SpongeEventFactory;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.cause.NamedCause;
import org.spongepowered.api.event.cause.entity.spawn.SpawnCause;
import org.spongepowered.api.event.cause.entity.spawn.SpawnType;
import org.spongepowered.api.event.entity.SpawnEntityEvent;
import org.spongepowered.api.event.item.inventory.ClickInventoryEvent;
import org.spongepowered.api.event.item.inventory.CreativeInventoryEvent;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;
import org.spongepowered.api.item.inventory.Slot;
import org.spongepowered.api.item.inventory.slot.OutputSlot;
import org.spongepowered.api.item.inventory.transaction.InventoryTransactionResult;
import org.spongepowered.api.item.inventory.transaction.SlotTransaction;
import org.spongepowered.api.world.World;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

/**
 * Represents a session of a player interacting with a
 * {@link LanternContainer}. It is possible to switch
 * between {@link LanternContainer}s without canceling
 * the session.
 *
 * This will for example keep the cursor item until it
 * is placed or the session is finished.
 */
public class PlayerContainerSession {

    private final LanternPlayer player;

    /**
     * The container that is currently open.
     */
    @Nullable private LanternContainer openContainer;

    /**
     * The item stack currently on the cursor
     */
    @Nullable private ItemStack cursorItem;

    /**
     * All the slots currently in a drag session.
     */
    private final List<LanternSlot> dragSlots = new ArrayList<>();

    /**
     * Whether the dragging was started with the left mouse button.
     *
     * -1 means not started
     *  0 means left drag
     *  1 means right drag
     */
    private int dragState = -1;

    public PlayerContainerSession(LanternPlayer player) {
        this.player = player;
    }

    /**
     * Gets the open container.
     *
     * @return The container
     */
    @Nullable
    public LanternContainer getOpenContainer() {
        return this.openContainer;
    }

    /**
     * Sets the open container.
     *
     * @param container The container
     */
    public void setOpenContainer(@Nullable LanternContainer container) {
        if (this.openContainer != null && container == null) {
            this.player.getConnection().send(
                    new MessagePlayInOutCloseWindow(this.openContainer.windowId));
        }
        this.setRawOpenContainer(container);
    }

    /**
     * Sets the open container.
     *
     * @param container The container
     */
    public void setRawOpenContainer(@Nullable LanternContainer container) {
        if (this.openContainer != container) {
            if (container != null) {
                container.viewers.add(this.player);
                container.openInventoryForAndInitialize(this.player);
                this.updateCursorItem();
            }
            if (this.openContainer != null) {
                if (container == null && this.cursorItem != null) {
                    // TODO: Drop the cursor item
                    this.openContainer.humanInventory.getInventoryView(HumanInventoryView.MAIN_AND_PRIORITY_HOTBAR)
                            .offer(this.cursorItem);
                    this.cursorItem = null;
                }
                this.openContainer.viewers.remove(this.player);
            }
        }
        this.openContainer = container;
    }

    /**
     * Sets the cursor item.
     *
     * @param cursorItem The cursor item
     */
    public void setCursorItem(@Nullable ItemStack cursorItem) {
        this.cursorItem = LanternItemStack.toNullable(cursorItem);
        this.updateCursorItem();
    }

    void updateCursorItem() {
        this.player.getConnection().send(
                new MessagePlayOutSetWindowSlot(-1, -1, this.cursorItem));
    }

    @Nullable
    public ItemStack getCursorItem() {
        return this.cursorItem;
    }

    public void handleWindowCreativeClick(MessagePlayInCreativeWindowAction message) {
        if (this.openContainer == null) {
            return;
        }
        ItemStack itemStack = LanternItemStack.toNullable(message.getItemStack());
        int slotIndex = message.getSlot();
        if (slotIndex < 0) {
            // TODO: Implement spawn causes
            SpawnType dummySpawnType = new SpawnType() {

                @Override
                public String getId() {
                    return "dropped_item";
                }

                @Override
                public String getName() {
                    return "dropped_item";
                }
            };
            SpawnCause spawnCause = () -> dummySpawnType;

            // Cause cause = Cause.builder().named("SpawnCause", SpawnCause.builder()
            //         .type(dummySpawnType).build()).named(NamedCause.SOURCE, this.player).build();
            Cause cause = Cause.builder().named("SpawnCause", spawnCause).named(NamedCause.SOURCE, this.player).build();
            List<Entity> entities = new ArrayList<>();
            World world = this.player.getWorld();

            Transaction<ItemStackSnapshot> cursorTransaction = new Transaction<>(
                    LanternItemStack.toSnapshot(itemStack), ItemStackSnapshot.NONE);

            CreativeInventoryEvent.Drop event = SpongeEventFactory.createCreativeInventoryEventDrop(
                    cause, cursorTransaction, entities, this.openContainer, world, new ArrayList<>());
            this.finishInventoryEvent(event);
        } else {
            Optional<LanternSlot> optSlot = this.openContainer.humanInventory.getSlotAt(slotIndex);
            if (optSlot.isPresent()) {
                final Cause cause = Cause.builder().named(NamedCause.SOURCE, this.player).build();
                final LanternSlot slot = optSlot.get();

                PeekSetTransactionsResult result = slot.peekSetTransactions(itemStack);

                // We do not know the remaining stack in the cursor,
                // so just use none as new item
                Transaction<ItemStackSnapshot> cursorTransaction = new Transaction<>(
                        LanternItemStack.toSnapshot(itemStack), ItemStackSnapshot.NONE);

                CreativeInventoryEvent.Click event = SpongeEventFactory.createCreativeInventoryEventClick(
                        cause, cursorTransaction, this.openContainer, result.getTransactions());
                this.finishInventoryEvent(event);
            } else {
                Lantern.getLogger().warn("Unknown slot index {} in container {}", slotIndex, this.openContainer);
            }
        }
    }

    private void resetDrag() {
        this.dragState = -1;
        this.dragSlots.clear();
    }

    public void handleWindowClick(MessagePlayInClickWindow message) {
        if (this.openContainer == null) {
            return;
        }
        int windowId = message.getWindowId();
        if (windowId != this.openContainer.windowId) {
            return;
        }
        int button = message.getButton();
        int mode = message.getMode();
        int slotIndex = message.getSlot();

        // Drag mode
        if (mode == 5) {
            if (this.cursorItem == null) {
                this.resetDrag();
            } else if (this.dragState != -1) {
                if ((this.dragState == 0 && (button == 1 || button == 2)) ||
                        (this.dragState == 1 && (button == 5 || button == 6))) {
                    if (button == 2 || button == 6) {
                        final Cause cause = Cause.builder().named(NamedCause.SOURCE, this.player).build();
                        if (button == 2) {
                            int quantity = this.cursorItem.getQuantity();
                            int slots = this.dragSlots.size();
                            int itemsPerSlot = quantity / slots;
                            int rest = quantity - itemsPerSlot * slots;

                            List<SlotTransaction> transactions = new ArrayList<>();
                            for (LanternSlot slot : this.dragSlots) {
                                ItemStack itemStack = this.cursorItem.copy();
                                itemStack.setQuantity(itemsPerSlot);
                                transactions.addAll(slot.peekOfferFastTransactions(itemStack).getTransactions());
                            }

                            ItemStackSnapshot newCursorItem = ItemStackSnapshot.NONE;
                            if (rest > 0) {
                                ItemStack itemStack = this.cursorItem.copy();
                                itemStack.setQuantity(rest);
                                newCursorItem = itemStack.createSnapshot();
                            }
                            ItemStackSnapshot oldCursorItem = this.cursorItem.createSnapshot();
                            Transaction<ItemStackSnapshot> cursorTransaction = new Transaction<>(oldCursorItem, newCursorItem);

                            ClickInventoryEvent.Drag.Primary event = SpongeEventFactory.createClickInventoryEventDragPrimary(
                                    cause, cursorTransaction, this.openContainer, transactions);
                            this.finishInventoryEvent(event);
                            this.resetDrag();
                        } else {
                            int quantity = this.cursorItem.getQuantity();
                            int size = Math.min(this.dragSlots.size(), quantity);

                            List<SlotTransaction> transactions = new ArrayList<>();
                            for (LanternSlot slot : this.dragSlots) {
                                ItemStack itemStack = this.cursorItem.copy();
                                itemStack.setQuantity(1);
                                transactions.addAll(slot.peekOfferFastTransactions(itemStack).getTransactions());
                            }
                            quantity -= size;

                            ItemStackSnapshot newCursorItem = ItemStackSnapshot.NONE;
                            if (quantity > 0) {
                                ItemStack itemStack = this.cursorItem.copy();
                                itemStack.setQuantity(quantity);
                                newCursorItem = itemStack.createSnapshot();
                            }
                            ItemStackSnapshot oldCursorItem = this.cursorItem.createSnapshot();
                            Transaction<ItemStackSnapshot> cursorTransaction = new Transaction<>(oldCursorItem, newCursorItem);

                            ClickInventoryEvent.Drag.Secondary event = SpongeEventFactory.createClickInventoryEventDragSecondary(
                                    cause, cursorTransaction, this.openContainer, transactions);
                            this.finishInventoryEvent(event);
                            this.resetDrag();
                        }
                    } else {
                        // Add slot
                        Optional<LanternSlot> optSlot = this.openContainer.getSlotAt(slotIndex);
                        if (optSlot.isPresent()) {
                            final LanternSlot slot = optSlot.get();
                            if (!(slot instanceof OutputSlot) && slot.isValidItem(this.cursorItem) && (slot.getRawItemStack() == null ||
                                    ((LanternItemStack) this.cursorItem).isEqualToOther(slot.getRawItemStack())) && !this.dragSlots.contains(slot)) {
                                this.dragSlots.add(slot);
                            }
                        }
                    }
                } else {
                    this.resetDrag();
                }
            } else if (button == 0) {
                this.dragState = 0;
            } else if (button == 4) {
                this.dragState = 1;
            }
        } else if (this.dragState != -1) {
            this.resetDrag();
        } else if (mode == 0 && (button == 0 || button == 1) && slotIndex != -999) {
            Optional<LanternSlot> optSlot = this.openContainer.getSlotAt(slotIndex);
            if (optSlot.isPresent()) {
                final LanternSlot slot = optSlot.get();
                final Cause cause = Cause.builder().named(NamedCause.SOURCE, this.player).build();

                ClickInventoryEvent event;
                if (button == 0) {
                    List<SlotTransaction> transactions = new ArrayList<>();
                    Transaction<ItemStackSnapshot> cursorTransaction = null;

                    if (this.cursorItem != null && !(slot instanceof OutputSlot)) {
                        PeekOfferTransactionsResult result = slot.peekOfferFastTransactions(this.cursorItem);
                        if (result.getOfferResult().isSuccess()) {
                            transactions.addAll(result.getTransactions());
                            cursorTransaction = new Transaction<>(this.cursorItem.createSnapshot(),
                                    LanternItemStack.toSnapshot(result.getOfferResult().getRest()));
                        } else {
                            PeekSetTransactionsResult result1 = slot.peekSetTransactions(this.cursorItem);
                            if (result1.getTransactionResult().getType().equals(InventoryTransactionResult.Type.SUCCESS)) {
                                Collection<ItemStackSnapshot> replaceItems = result1.getTransactionResult().getReplacedItems();
                                if (!replaceItems.isEmpty()) {
                                    cursorTransaction = new Transaction<>(this.cursorItem.createSnapshot(),
                                            replaceItems.iterator().next());
                                } else {
                                    cursorTransaction = new Transaction<>(this.cursorItem.createSnapshot(),
                                            ItemStackSnapshot.NONE);
                                }
                                transactions.addAll(result1.getTransactions());
                            }
                        }
                    } else if (this.cursorItem == null) {
                        PeekPollTransactionsResult result = slot.peekPollTransactions(stack -> true).orElse(null);
                        if (result != null) {
                            cursorTransaction = new Transaction<>(ItemStackSnapshot.NONE, LanternItemStack.toSnapshot(result.getPeekedItem()));
                            transactions.addAll(result.getTransactions());
                        } else {
                            cursorTransaction = new Transaction<>(ItemStackSnapshot.NONE, ItemStackSnapshot.NONE);
                        }
                    }
                    if (cursorTransaction == null) {
                        ItemStackSnapshot cursorItem = LanternItemStack.toSnapshot(this.cursorItem);
                        cursorTransaction = new Transaction<>(cursorItem, cursorItem);
                    }
                    event = SpongeEventFactory.createClickInventoryEventPrimary(cause, cursorTransaction, this.openContainer, transactions);
                } else {
                    List<SlotTransaction> transactions = new ArrayList<>();
                    Transaction<ItemStackSnapshot> cursorTransaction = null;

                    if (this.cursorItem == null) {
                        int stackSize = slot.getStackSize();
                        if (stackSize != 0) {
                            stackSize = stackSize - (stackSize / 2);
                            PeekPollTransactionsResult result = slot.peekPollTransactions(stackSize, stack -> true).get();
                            transactions.addAll(result.getTransactions());
                            cursorTransaction = new Transaction<>(ItemStackSnapshot.NONE, result.getPeekedItem().createSnapshot());
                        }
                    } else {
                        final ItemStack itemStack = this.cursorItem.copy();
                        itemStack.setQuantity(1);

                        PeekOfferTransactionsResult result = slot.peekOfferFastTransactions(itemStack);
                        if (result.getOfferResult().isSuccess()) {
                            int quantity = this.cursorItem.getQuantity() - 1;
                            if (quantity <= 0) {
                                cursorTransaction = new Transaction<>(this.cursorItem.createSnapshot(), ItemStackSnapshot.NONE);
                            } else {
                                ItemStack cursorItem = this.cursorItem.copy();
                                cursorItem.setQuantity(quantity);
                                cursorTransaction = new Transaction<>(cursorItem.createSnapshot(), ItemStackSnapshot.NONE);
                            }
                        } else {
                            PeekSetTransactionsResult result1 = slot.peekSetTransactions(this.cursorItem);
                            if (result1.getTransactionResult().getType().equals(InventoryTransactionResult.Type.SUCCESS)) {
                                Collection<ItemStackSnapshot> replaceItems = result1.getTransactionResult().getReplacedItems();
                                if (!replaceItems.isEmpty()) {
                                    this.setCursorItem(replaceItems.iterator().next().createStack());
                                    cursorTransaction = new Transaction<>(this.cursorItem.createSnapshot(),
                                            replaceItems.iterator().next());
                                } else {
                                    cursorTransaction = new Transaction<>(this.cursorItem.createSnapshot(),
                                            ItemStackSnapshot.NONE);
                                }
                                transactions.addAll(result1.getTransactions());
                            }
                        }
                    }
                    if (cursorTransaction == null) {
                        ItemStackSnapshot cursorItem = LanternItemStack.toSnapshot(this.cursorItem);
                        cursorTransaction = new Transaction<>(cursorItem, cursorItem);
                    }
                    event = SpongeEventFactory.createClickInventoryEventSecondary(cause, cursorTransaction, this.openContainer, transactions);
                }
                this.finishInventoryEvent(event);
            } else {
                Lantern.getLogger().warn("Unknown slot index {} in container {}", slotIndex, this.openContainer);
            }
        } else if (mode == 1 && (button == 0 || button == 1)) {
            Optional<LanternSlot> optSlot = this.openContainer.getSlotAt(slotIndex);
            if (optSlot.isPresent()) {
                final LanternSlot slot = optSlot.get();
                final ItemStack itemStack = slot.peek().orElse(null);

                final Cause cause = Cause.builder().named(NamedCause.SOURCE, this.player).build();
                List<SlotTransaction> transactions = new ArrayList<>();

                ItemStackSnapshot cursorItem = LanternItemStack.toSnapshot(this.cursorItem);
                Transaction<ItemStackSnapshot> cursorTransaction = new Transaction<>(cursorItem, cursorItem);

                if (itemStack != null) {
                    InventoryBase inventory;

                    HumanMainInventory mainInventory = this.openContainer.humanInventory
                            .query(HumanMainInventory.class).first();
                    if ((windowId != 0 && this.openContainer.openInventory.getSlotIndex(slot) != -1) ||
                            (windowId == 0 && !mainInventory.isChild(slot))) {
                        if (slot.isReverseShiftClickOfferOrder()) {
                            inventory = this.openContainer.humanInventory.getInventoryView(HumanInventoryView.REVERSE_MAIN_AND_HOTBAR);
                        } else {
                            inventory = this.openContainer.humanInventory.getInventoryView(HumanInventoryView.PRIORITY_MAIN_AND_HOTBAR);
                        }
                    } else {
                        inventory = this.openContainer.openInventory.query(inv -> !mainInventory.isChild(inv) && inv instanceof Slot &&
                                ((LanternSlot) inv).doesAllowShiftClickOffer() && !(inv instanceof OutputSlot), false);
                        if (!inventory.isValidItem(itemStack)) {
                            if (slot.parent() instanceof LanternHotbar) {
                                inventory = this.openContainer.humanInventory.getInventoryView(HumanInventoryView.MAIN);
                            } else {
                                inventory = this.openContainer.humanInventory.getHotbar();
                            }
                        }
                    }

                    PeekOfferTransactionsResult result = inventory.peekOfferFastTransactions(itemStack.copy());
                    if (result.getOfferResult().isSuccess()) {
                        transactions.addAll(result.getTransactions());
                        ItemStack rest = result.getOfferResult().getRest();
                        if (rest != null) {
                            transactions.addAll(slot.peekPollTransactions(
                                    itemStack.getQuantity() - rest.getQuantity(), stack -> true).get().getTransactions());
                        } else {
                            transactions.addAll(slot.peekPollTransactions(
                                    stack -> true).get().getTransactions());
                        }
                    }
                }

                ClickInventoryEvent.Shift event;
                if (button == 0) {
                    event = SpongeEventFactory.createClickInventoryEventShiftPrimary(
                            cause, cursorTransaction, this.openContainer, transactions);
                } else {
                    event = SpongeEventFactory.createClickInventoryEventShiftSecondary(
                            cause, cursorTransaction, this.openContainer, transactions);
                }

                this.finishInventoryEvent(event);
            } else {
                Lantern.getLogger().warn("Unknown slot index {} in container {}", slotIndex, this.openContainer);
            }
        } else if (mode == 6 && button == 0) {
            Optional<LanternSlot> optSlot = this.openContainer.getSlotAt(slotIndex);
            if (optSlot.isPresent()) {
                final Cause cause = Cause.builder().named(NamedCause.SOURCE, this.player).build();
                final ItemStackSnapshot oldItem = LanternItemStack.toSnapshot(this.cursorItem);
                ItemStackSnapshot newItem = oldItem;

                final List<SlotTransaction> transactions = new ArrayList<>();

                if (this.cursorItem != null) {
                    ItemStack cursorItem = this.cursorItem.copy();
                    int quantity = cursorItem.getQuantity();
                    int maxQuantity = cursorItem.getMaxStackQuantity();
                    if (quantity < maxQuantity) {
                        InventoryBase inventory;
                        if (windowId != 0) {
                            inventory = new ChildrenInventoryBase(null, null, Arrays.asList(
                                    this.openContainer.openInventory, this.openContainer.humanInventory
                                            .getInventoryView(HumanInventoryView.PRIORITY_MAIN_AND_HOTBAR)));
                        } else {
                            inventory = this.openContainer.humanInventory
                                    .getInventoryView(HumanInventoryView.ALL_PRIORITY_MAIN);
                        }

                        // Try first to get enough unfinished stacks
                        PeekPollTransactionsResult peekResult = inventory.peekPollTransactions(maxQuantity - quantity, stack ->
                                stack.getQuantity() < stack.getMaxStackQuantity() &&
                                        ((LanternItemStack) cursorItem).isEqualToOther(stack)).orElse(null);
                        if (peekResult != null) {
                            quantity += peekResult.getPeekedItem().getQuantity();
                            transactions.addAll(peekResult.getTransactions());
                        }
                        // Get the last items for the stack from a full stack
                        if (quantity <= maxQuantity) {
                            peekResult = this.openContainer.peekPollTransactions(maxQuantity - quantity, stack ->
                                    stack.getQuantity() >= stack.getMaxStackQuantity() &&
                                            ((LanternItemStack) cursorItem).isEqualToOther(stack)).orElse(null);
                            if (peekResult != null) {
                                quantity += peekResult.getPeekedItem().getQuantity();
                                transactions.addAll(peekResult.getTransactions());
                            }
                        }
                        cursorItem.setQuantity(quantity);
                        newItem = cursorItem.createSnapshot();
                    }
                }

                Transaction<ItemStackSnapshot> cursorTransaction = new Transaction<>(oldItem, newItem);
                ClickInventoryEvent.Double event = SpongeEventFactory.createClickInventoryEventDouble(
                        cause, cursorTransaction, this.openContainer, transactions);

                this.finishInventoryEvent(event);
            } else {
                Lantern.getLogger().warn("Unknown slot index {} in container {}", slotIndex, this.openContainer);
            }
        } else if (mode == 2) {
            Optional<LanternSlot> optSlot = this.openContainer.getSlotAt(slotIndex);
            if (optSlot.isPresent()) {
                final LanternSlot slot = optSlot.get();

                LanternHotbar hotbar = this.openContainer.humanInventory.query(LanternHotbar.class).first();
                Optional<LanternSlot> optHotbarSlot = hotbar.getSlotAt(button);
                if (optHotbarSlot.isPresent()) {
                    final LanternSlot hotbarSlot = optHotbarSlot.get();

                    final Cause cause = Cause.builder().named(NamedCause.SOURCE, this.player).build();
                    final List<SlotTransaction> transactions = new ArrayList<>();

                    Transaction<ItemStackSnapshot> cursorTransaction;

                    if (this.cursorItem == null) {
                        cursorTransaction = new Transaction<>(ItemStackSnapshot.NONE, ItemStackSnapshot.NONE);

                        ItemStack otherItemStack = slot.getRawItemStack();
                        ItemStack hotbarItemStack = hotbarSlot.getRawItemStack();

                        ItemStackSnapshot otherItem = LanternItemStack.toSnapshot(otherItemStack);
                        ItemStackSnapshot hotbarItem = LanternItemStack.toSnapshot(hotbarItemStack);

                        if (!(otherItem != ItemStackSnapshot.NONE && (!hotbarSlot.isValidItem(otherItemStack) ||
                                otherItemStack.getQuantity() > hotbarSlot.getMaxStackSize())) &&
                                !(hotbarItem != ItemStackSnapshot.NONE && (!slot.isValidItem(hotbarItemStack) ||
                                        hotbarItemStack.getQuantity() > slot.getMaxStackSize()))) {
                            transactions.add(new SlotTransaction(slot, otherItem, hotbarItem));
                            transactions.add(new SlotTransaction(hotbarSlot, hotbarItem, otherItem));
                        }
                    } else {
                        ItemStackSnapshot cursorItem = this.cursorItem.createSnapshot();
                        cursorTransaction = new Transaction<>(cursorItem, cursorItem);
                    }

                    ClickInventoryEvent.NumberPress event = SpongeEventFactory.createClickInventoryEventNumberPress(
                            cause, cursorTransaction, this.openContainer, transactions, button);
                    this.finishInventoryEvent(event);
                } else {
                    Lantern.getLogger().warn("Unknown hotbar slot index {}", mode);
                }
            } else {
                Lantern.getLogger().warn("Unknown slot index {} in container {}", slotIndex, this.openContainer);
            }
        } else if ((mode == 4 || mode == 0) && (button == 0 || button == 1)) {
            ClickInventoryEvent.Drop event = null;

            // TODO: Implement spawn causes
            SpawnType dummySpawnType = new SpawnType() {

                @Override
                public String getId() {
                    return "dropped_item";
                }

                @Override
                public String getName() {
                    return "dropped_item";
                }
            };
            SpawnCause spawnCause = () -> dummySpawnType;

            // Cause cause = Cause.builder().named("SpawnCause", SpawnCause.builder()
            //         .type(dummySpawnType).build()).named(NamedCause.SOURCE, this.player).build();
            Cause cause = Cause.builder().named("SpawnCause", spawnCause).named(NamedCause.SOURCE, this.player).build();
            List<Entity> entities = new ArrayList<>();
            World world = this.player.getWorld();

            Transaction<ItemStackSnapshot> cursorTransaction;
            List<SlotTransaction> slotTransactions = new ArrayList<>();

            if (slotIndex == -999) {
                ItemStackSnapshot oldItem = ItemStackSnapshot.NONE;
                ItemStackSnapshot newItem = ItemStackSnapshot.NONE;
                if (this.cursorItem != null) {
                    if (button == 0) {
                        oldItem = this.cursorItem.createSnapshot();
                    } else {
                        ItemStack stack = this.cursorItem.copy();
                        stack.setQuantity(stack.getQuantity() - 1);
                        newItem = LanternItemStack.toSnapshot(stack);
                        stack.setQuantity(1);
                        oldItem = stack.createSnapshot();
                    }
                }
                cursorTransaction = new Transaction<>(oldItem, newItem);
                if (button == 0) {
                    event = SpongeEventFactory.createClickInventoryEventDropOutsidePrimary(cause, cursorTransaction, entities,
                            this.openContainer, world, slotTransactions);
                } else {
                    event = SpongeEventFactory.createClickInventoryEventDropOutsideSecondary(cause, cursorTransaction, entities,
                            this.openContainer, world, slotTransactions);
                }
                // TODO: Add entity
            } else {
                ItemStackSnapshot item = LanternItemStack.toSnapshot(this.cursorItem);
                cursorTransaction = new Transaction<>(item, item);
                Optional<LanternSlot> optSlot = this.openContainer.getSlotAt(slotIndex);
                if (optSlot.isPresent()) {
                    final LanternSlot slot = optSlot.get();
                    Optional<ItemStack> stack = button == 0 ? slot.peek(1) : slot.peek();
                    slotTransactions.add(new SlotTransaction(slot, stack.map(ItemStack::createSnapshot)
                            .orElse(ItemStackSnapshot.NONE), ItemStackSnapshot.NONE));
                    if (button == 0) {
                        event = SpongeEventFactory.createClickInventoryEventDropSingle(cause, cursorTransaction, entities,
                                this.openContainer, world, slotTransactions);
                    } else {
                        event = SpongeEventFactory.createClickInventoryEventDropFull(cause, cursorTransaction, entities,
                                this.openContainer, world, slotTransactions);
                    }
                    // TODO: Add entity
                } else {
                    Lantern.getLogger().warn("Unknown slot index {} in container {}", slotIndex, this.openContainer);
                }
            }
            if (event != null) {
                this.finishInventoryEvent(event);
            }
        } else if (mode == 3) {
            final Cause cause = Cause.builder().named(NamedCause.SOURCE, this.player).build();
            final ItemStackSnapshot oldItem = LanternItemStack.toSnapshot(this.cursorItem);
            Transaction<ItemStackSnapshot> cursorTransaction = null;

            Optional<GameMode> gameMode = this.player.get(Keys.GAME_MODE);
            if (gameMode.isPresent() && gameMode.get().equals(GameModes.CREATIVE)
                    && this.cursorItem == null) {
                Optional<LanternSlot> optSlot = this.openContainer.getSlotAt(slotIndex);
                if (optSlot.isPresent()) {
                    final LanternSlot slot = optSlot.get();
                    ItemStack stack = slot.peek().orElse(null);
                    if (stack != null) {
                        stack.setQuantity(stack.getMaxStackQuantity());
                        cursorTransaction = new Transaction<>(oldItem, stack.createSnapshot());
                    }
                } else {
                    Lantern.getLogger().warn("Unknown slot index {} in container {}", slotIndex, this.openContainer);
                }
            }
            if (cursorTransaction == null) {
                cursorTransaction = new Transaction<>(oldItem, oldItem);
            }

            ClickInventoryEvent.Middle event = SpongeEventFactory.createClickInventoryEventMiddle(
                    cause, cursorTransaction, this.openContainer, new ArrayList<>());
            this.finishInventoryEvent(event);
        }
    }

    private void finishInventoryEvent(ClickInventoryEvent event) {
        List<SlotTransaction> slotTransactions = event.getTransactions();
        Sponge.getEventManager().post(event);
        if (!event.isCancelled()) {
            if (!(event instanceof CreativeInventoryEvent.Click)) {
                Transaction<ItemStackSnapshot> cursorTransaction = event.getCursorTransaction();
                if (!cursorTransaction.isValid()) {
                    this.updateCursorItem();
                } else {
                    this.setCursorItem(cursorTransaction.getFinal().createStack());
                }
            }
            for (SlotTransaction slotTransaction : slotTransactions) {
                if (slotTransaction.isValid()) {
                    slotTransaction.getSlot().set(slotTransaction.getFinal().createStack());
                } else {
                    // Force the slot to update
                    this.openContainer.queueSlotChange(slotTransaction.getSlot());
                }
            }
            if (event instanceof SpawnEntityEvent) {
                // TODO: Spawn entities
            }
        } else {
            this.updateCursorItem();
            for (SlotTransaction slotTransaction : slotTransactions) {
                // Force the slot to update
                this.openContainer.queueSlotChange(slotTransaction.getSlot());
            }
        }
    }
}