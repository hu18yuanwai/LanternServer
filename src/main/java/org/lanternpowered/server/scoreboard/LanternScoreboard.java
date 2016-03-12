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
package org.lanternpowered.server.scoreboard;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import org.lanternpowered.server.entity.living.player.LanternPlayer;
import org.lanternpowered.server.network.message.Message;
import org.lanternpowered.server.network.vanilla.message.type.play.MessagePlayOutScoreboardDisplayObjective;
import org.lanternpowered.server.network.vanilla.message.type.play.MessagePlayOutScoreboardObjective;
import org.lanternpowered.server.network.vanilla.message.type.play.MessagePlayOutScoreboardScore;
import org.lanternpowered.server.text.LanternTexts;
import org.spongepowered.api.scoreboard.Score;
import org.spongepowered.api.scoreboard.Scoreboard;
import org.spongepowered.api.scoreboard.Team;
import org.spongepowered.api.scoreboard.critieria.Criterion;
import org.spongepowered.api.scoreboard.displayslot.DisplaySlot;
import org.spongepowered.api.scoreboard.objective.Objective;
import org.spongepowered.api.text.Text;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import javax.annotation.Nullable;

public class LanternScoreboard implements Scoreboard {

    private final Set<LanternPlayer> players = Sets.newHashSet();
    private final Map<String, Objective> objectives = Maps.newHashMap();
    private final Multimap<Criterion, Objective> objectivesByCriterion = HashMultimap.create();
    private final Map<DisplaySlot, Objective> objectivesInSlot = Maps.newHashMap();

    void sendToPlayers(Supplier<List<Message>> messageSupplier) {
        if (!this.players.isEmpty()) {
            List<Message> messages = messageSupplier.get();
            this.players.forEach(player -> player.getConnection().sendAll(messages));
        }
    }

    public void removePlayer(LanternPlayer player) {
        this.players.remove(player);
        for (Objective objective : this.objectives.values()) {
            player.getConnection().send(new MessagePlayOutScoreboardObjective.Remove(objective.getName()));
        }
    }

    public void addPlayer(LanternPlayer player) {
        this.players.add(player);
        for (Objective objective : this.objectives.values()) {
            player.getConnection().sendAll(this.createObjectiveInitMessages(objective));
        }
        for (Map.Entry<DisplaySlot, Objective> entry : this.objectivesInSlot.entrySet()) {
            player.getConnection().send(new MessagePlayOutScoreboardDisplayObjective(entry.getValue().getName(), entry.getKey()));
        }
    }

    @Override
    public Optional<Objective> getObjective(String name) {
        return Optional.ofNullable(this.objectives.get(checkNotNull(name, "name")));
    }

    @Override
    public Optional<Objective> getObjective(DisplaySlot slot) {
        return Optional.ofNullable(this.objectivesInSlot.get(checkNotNull(slot, "slot")));
    }

    @Override
    public void addObjective(Objective objective) throws IllegalArgumentException {
        checkNotNull(objective, "objective");
        checkArgument(!this.objectives.containsKey(objective.getName()), "A score with the name %s already exists!",
                objective.getName());
        this.objectives.put(objective.getName(), objective);
        this.objectivesByCriterion.put(objective.getCriterion(), objective);
        ((LanternObjective) objective).addScoreboard(this);
        // Create the scoreboard objective on the client
        this.sendToPlayers(() -> this.createObjectiveInitMessages(objective));
    }

    private List<Message> createObjectiveInitMessages(Objective objective) {
        List<Message> messages = Lists.newArrayList();
        messages.add(new MessagePlayOutScoreboardObjective.Create(
                objective.getName(), objective.getDisplayName(), objective.getDisplayMode()));
        for (Score score : ((LanternObjective) objective).scores.values()) {
            messages.add(new MessagePlayOutScoreboardScore.CreateOrUpdate(objective.getName(),
                    LanternTexts.toLegacy(score.getName()), score.getScore()));
        }
        return messages;
    }

    @Override
    public void updateDisplaySlot(@Nullable Objective objective, DisplaySlot displaySlot) throws IllegalStateException {
        checkNotNull(displaySlot, "displaySlot");
        if (objective == null) {
            Objective oldObjective = this.objectivesInSlot.remove(displaySlot);
            if (oldObjective != null) {
                // Clear the display slot on the client
                this.sendToPlayers(() -> Collections.singletonList(
                        new MessagePlayOutScoreboardDisplayObjective(null, displaySlot)));
            }
        } else {
            checkState(this.objectives.containsValue(objective),
                    "The specified objective does not exist in this scoreboard.");
            if (this.objectivesInSlot.put(displaySlot, objective) != objective) {
                // Update the displayed objective on the client
                this.sendToPlayers(() -> Collections.singletonList(
                        new MessagePlayOutScoreboardDisplayObjective(objective.getName(), displaySlot)));
            }
        }
    }

    @Override
    public Set<Objective> getObjectivesByCriteria(Criterion criteria) {
        return ImmutableSet.copyOf(this.objectivesByCriterion.get(checkNotNull(criteria, "criteria")));
    }

    @Override
    public Set<Objective> getObjectives() {
        return ImmutableSet.copyOf(this.objectives.values());
    }

    @Override
    public void removeObjective(Objective objective) {
        if (this.objectives.remove(checkNotNull(objective, "objective").getName(), objective)) {
            ((LanternObjective) objective).removeScoreboard(this);
            this.objectivesByCriterion.remove(objective.getCriterion(), objective);
            Iterator<Map.Entry<DisplaySlot, Objective>> it = this.objectivesInSlot.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<DisplaySlot, Objective> entry = it.next();
                if (entry.getValue().equals(objective)) {
                    it.remove();
                }
            }
            this.sendToPlayers(() -> Collections.singletonList(new MessagePlayOutScoreboardObjective.Remove(objective.getName())));
        }
    }

    @Override
    public Set<Score> getScores() {
        ImmutableSet.Builder<Score> scores = ImmutableSet.builder();
        for (Objective objective : this.objectives.values()) {
            scores.addAll(((LanternObjective) objective).scores.values());
        }
        return scores.build();
    }

    @Override
    public Set<Score> getScores(Text name) {
        checkNotNull(name, "name");
        ImmutableSet.Builder<Score> scores = ImmutableSet.builder();
        for (Objective objective : this.objectives.values()) {
            objective.getScore(name).ifPresent(scores::add);
        }
        return scores.build();
    }

    @Override
    public void removeScores(Text name) {
        checkNotNull(name, "name");
        for (Objective objective : this.objectives.values()) {
            objective.removeScore(name);
        }
    }

    @Override
    public Optional<Team> getTeam(String teamName) {
        return Optional.empty();
    }

    @Override
    public void registerTeam(Team team) throws IllegalArgumentException {

    }

    @Override
    public Set<Team> getTeams() {
        return ImmutableSet.of();
    }

    @Override
    public Optional<Team> getMemberTeam(Text member) {
        return Optional.empty();
    }
}
