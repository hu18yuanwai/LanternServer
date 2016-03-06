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
package org.lanternpowered.server.command;

import static org.lanternpowered.server.text.translation.TranslationHelper.t;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.lanternpowered.server.command.element.RemainingTextElement;
import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.serializer.TextParseException;
import org.spongepowered.api.text.serializer.TextSerializers;
import org.spongepowered.api.text.title.Title;

public final class CommandTitle {

    public static final String PERMISSION = "minecraft.command.title";

    public static CommandSpec create() {
        return CommandSpec.builder().children(
                ImmutableMap.of(
                        Lists.newArrayList("clear"), CommandSpec.builder()
                                .executor((src, args) -> {
                                    args.<Player>getOne("player").get().clearTitle();
                                    return CommandResult.success();
                                })
                                .build(),
                        Lists.newArrayList("title"), CommandSpec.builder()
                                .arguments(RemainingTextElement.of(Text.of("title")))
                                .executor((src, args) -> {
                                    Text title;
                                    try {
                                        title = TextSerializers.JSON.deserialize(args.<String>getOne("title").get());
                                    } catch (TextParseException e) {
                                        throw new CommandException(t("commands.tellraw.jsonException", e.getMessage()));
                                    }
                                    args.<Player>getOne("player").get().sendTitle(Title.builder().title(title).build());
                                    return CommandResult.success();
                                })
                                .build(),
                        Lists.newArrayList("subtitle"), CommandSpec.builder()
                                .arguments(RemainingTextElement.of(Text.of("title")))
                                .executor((src, args) -> {
                                    Text title;
                                    try {
                                        title = TextSerializers.JSON.deserialize(args.<String>getOne("title").get());
                                    } catch (TextParseException e) {
                                        throw new CommandException(t("commands.tellraw.jsonException", e.getMessage()));
                                    }
                                    args.<Player>getOne("player").get().sendTitle(Title.builder().subtitle(title).build());
                                    return CommandResult.success();
                                })
                                .build(),
                        Lists.newArrayList("reset"), CommandSpec.builder()
                                .executor((src, args) -> {
                                    args.<Player>getOne("player").get().resetTitle();
                                    return CommandResult.success();
                                })
                                .build(),
                        Lists.newArrayList("times"), CommandSpec.builder()
                                .arguments(
                                        GenericArguments.integer(Text.of("fadeIn")),
                                        GenericArguments.integer(Text.of("stay")),
                                        GenericArguments.integer(Text.of("fadeOut")))
                                .executor((src, args) -> {
                                    args.<Player>getOne("player").get().sendTitle(Title.builder()
                                            .fadeIn(args.<Integer>getOne("fadeIn").get())
                                            .stay(args.<Integer>getOne("stay").get())
                                            .fadeOut(args.<Integer>getOne("fadeOut").get())
                                            .build());
                                    return CommandResult.success();
                                })
                                .build()))
                .arguments(
                        GenericArguments.player(Text.of("player")))
                .permission(PERMISSION)
                .build();
    }

    private CommandTitle() {
    }

}