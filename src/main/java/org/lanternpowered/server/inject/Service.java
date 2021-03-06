/*
 * This file is part of LanternServer, licensed under the MIT License (MIT).
 *
 * Copyright (c) LanternPowered <https://www.lanternpowered.org>
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
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
package org.lanternpowered.server.inject;

import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.service.ProviderRegistration;

import java.util.Optional;

@FunctionalInterface
public interface Service<T> {

    /**
     * Gets the {@link ProviderRegistration<T>}.
     *
     * @return The registration
     */
    ProviderRegistration<T> getRegistration();

    /**
     * Gets the service instance.
     *
     * @return The service instance
     */
    default T get() {
        return getRegistration().getProvider();
    }

    /**
     * Gets the service type.
     *
     * @return The service type
     */
    default Class<T> getType() {
        return getRegistration().getService();
    }

    /**
     * Gets the {@link PluginContainer} that registered the service instance.
     *
     * @return The plugin container for this service
     */
    default PluginContainer getPlugin() {
        return getRegistration().getPlugin();
    }

    default <E extends T> Optional<E> extended(Class<E> type) {
        final T instance = get();
        return type.isInstance(instance) ? Optional.of(type.cast(instance)) : Optional.empty();
    }
}
