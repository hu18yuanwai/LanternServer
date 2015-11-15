/*
 * This file is part of LanternServer, licensed under the MIT License (MIT).
 *
 * Copyright (c) LanternPowered <https://github.com/LanternPowered/LanternServer>
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
package org.lanternpowered.server.util.gen;

import com.flowpowered.math.vector.Vector3i;
import com.google.common.base.MoreObjects;

import org.lanternpowered.server.util.VecHelper;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.util.PositionOutOfBoundsException;
import org.spongepowered.api.util.annotation.NonnullByDefault;
import org.spongepowered.api.world.extent.BlockVolume;
import org.spongepowered.api.world.extent.MutableBlockVolume;
import org.spongepowered.api.world.extent.StorageType;

/**
 * Base class for block buffers.
 *
 */
@NonnullByDefault
public abstract class AbstractBlockBuffer implements BlockVolume {

    protected final Vector3i start;
    protected final Vector3i size;
    protected final Vector3i end;
    private final int yLine;
    private final int yzSlice;

    protected AbstractBlockBuffer(Vector3i start, Vector3i size) {
        this.start = start;
        this.size = size;
        this.end = this.start.add(this.size).sub(Vector3i.ONE);

        this.yLine = size.getY();
        this.yzSlice = this.yLine * size.getZ();
    }

    protected void checkRange(int x, int y, int z) {
        if (!VecHelper.inBounds(x, y, z, this.start, this.end)) {
            throw new PositionOutOfBoundsException(new Vector3i(x, y, z), this.start, this.end);
        }
    }

    public int getIndex(int x, int y, int z) {
        // return (x - this.start.getX()) * this.yzSlice + (z - this.start.getZ()) * this.yLine + (y - this.start.getY());

        // Using a different formula to make it easier to copy the contents of the array,
        // this should increase the performance wile generating chunks
        return (y - this.start.getY()) * this.yzSlice + (z - this.start.getZ()) * this.yLine + (x - this.start.getX());
    }

    @Override
    public Vector3i getBlockMax() {
        return this.end;
    }

    @Override
    public Vector3i getBlockMin() {
        return this.start;
    }

    @Override
    public Vector3i getBlockSize() {
        return this.size;
    }

    @Override
    public boolean containsBlock(Vector3i position) {
        return this.containsBlock(position.getX(), position.getY(), position.getZ());
    }

    @Override
    public boolean containsBlock(int x, int y, int z) {
        return VecHelper.inBounds(x, y, z, this.start, this.end);
    }

    @Override
    public BlockState getBlock(Vector3i position) {
        return this.getBlock(position.getX(), position.getY(), position.getZ());
    }

    @Override
    public BlockType getBlockType(Vector3i position) {
        return this.getBlockType(position.getX(), position.getY(), position.getZ());
    }

    @Override
    public BlockType getBlockType(int x, int y, int z) {
        return this.getBlock(x, y, z).getType();
    }

    @Override
    public MutableBlockVolume getBlockCopy() {
        return this.getBlockCopy(StorageType.STANDARD);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("min", this.getBlockMin())
            .add("max", this.getBlockMax())
            .toString();
    }

}