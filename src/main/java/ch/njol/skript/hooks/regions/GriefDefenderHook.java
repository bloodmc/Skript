/**
 *   This file is part of Skript.
 *
 *  Skript is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Skript is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Skript.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Copyright Peter Güttinger, SkriptLang team and contributors
 */
package ch.njol.skript.hooks.regions;

import java.io.IOException;
import java.io.StreamCorruptedException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.griefdefender.api.GriefDefender;
import com.griefdefender.api.User;
import com.griefdefender.api.claim.Claim;
import com.griefdefender.loader.BukkitLoaderPlugin;

import ch.njol.skript.hooks.regions.classes.Region;
import ch.njol.skript.util.AABB;
import ch.njol.skript.variables.Variables;
import ch.njol.util.coll.iterator.EmptyIterator;
import ch.njol.yggdrasil.Fields;
import ch.njol.yggdrasil.YggdrasilID;

public class GriefDefenderHook extends RegionsPlugin<BukkitLoaderPlugin> {

    public GriefDefenderHook() throws IOException {}

    @Override
    public String getName() {
        return "GriefDefender";
    }

    @Override
    public boolean canBuild_i(final Player p, final Location l) {
        final Claim claim = GriefDefender.getCore().getClaimAt(l);
        if (claim != null) {
            final User user = GriefDefender.getCore().getUser(p.getUniqueId());
            return claim.canBreak(p, l, user);
        }
        return false;
    }

    static {
        Variables.yggdrasil.registerSingleClass(GriefDefenderRegion.class);
    }

    @YggdrasilID("GriefDefenderRegion")
    public final class GriefDefenderRegion extends Region {
        
        private transient Claim claim;
        
        @SuppressWarnings({"unused"})
        private GriefDefenderRegion() {}
        
        public GriefDefenderRegion(final Claim c) {
            claim = c;
        }
        
        @Override
        public boolean contains(final Location l) {
            return claim.contains(l.getBlockX(), l.getBlockY(), l.getBlockZ());
        }

        @Override
        public boolean isMember(final OfflinePlayer p) {
            return isOwner(p);
        }

        @Override
        public Collection<OfflinePlayer> getMembers() {
            return getOwners();
        }

        @Override
        public boolean isOwner(final OfflinePlayer p) {
            return p.getUniqueId().equals(claim.getOwnerUniqueId());
        }

        @Override
        public Collection<OfflinePlayer> getOwners() {
            if (claim.isAdminClaim() || GriefDefender.getCore().getAdminUser().getUniqueId().equals(claim.getOwnerUniqueId()))
                return Collections.emptyList();
            else
                return Arrays.asList(Bukkit.getOfflinePlayer(claim.getOwnerUniqueId()));
        }

        @Override
        public Iterator<Block> getBlocks() {
            final World world = Bukkit.getWorld(claim.getWorldUniqueId());
            final int sx = claim.getLesserBoundaryCorner().getX();
            final int sy = claim.getLesserBoundaryCorner().getY();
            final int sz = claim.getLesserBoundaryCorner().getZ();
            final int bx = claim.getGreaterBoundaryCorner().getX();
            final int by = claim.getGreaterBoundaryCorner().getY();
            final int bz = claim.getGreaterBoundaryCorner().getZ();
            final Location lower = new Location(world, sx, sy, sz);
            final Location upper = new Location(world, bx, by, bz);
            if (lower == null || upper == null || lower.getWorld() == null || upper.getWorld() == null || lower.getWorld() != upper.getWorld())
                return EmptyIterator.get();
            upper.setY(upper.getWorld().getMaxHeight() - 1);
            upper.setX(upper.getBlockX());
            upper.setZ(upper.getBlockZ());
            return new AABB(lower, upper).iterator();
        }

        @Override
        public String toString() {
            return "Claim #" + claim.getUniqueId();
        }

        @Override
        public Fields serialize() {
            final Fields f = new Fields();
            f.putObject("id", claim.getUniqueId());
            return f;
        }

        @Override
        public void deserialize(final Fields fields) throws StreamCorruptedException {
            final UUID id = fields.getObject("id", UUID.class);
            final Claim c = GriefDefender.getCore().getClaim(id);
            if (c == null)
                throw new StreamCorruptedException("Invalid claim " + id);
            claim = c;
        }

        @Override
        public RegionsPlugin<?> getPlugin() {
            return GriefDefenderHook.this;
        }

        @Override
        public boolean equals(final @Nullable Object o) {
            if (o == this)
                return true;
            if (o == null)
                return false;
            if (!(o instanceof GriefDefenderRegion))
                return false;
            return claim.equals(((GriefDefenderRegion) o).claim);
        }

        @Override
        public int hashCode() {
            return claim.hashCode();
        }
    }

    @Override
    public Collection<? extends Region> getRegionsAt_i(final Location l) {
        final Claim c = GriefDefender.getCore().getClaimAt(l);
        if (c != null)
            return Arrays.asList(new GriefDefenderRegion(c));
        return Collections.emptySet();
    }

    @Override
    @Nullable
    public Region getRegion_i(final World world, final String name) {
        try {
            Claim c = null;
            if (name.length() == 36) {
                final UUID uuid = UUID.fromString(name);
                c = GriefDefender.getCore().getClaim(uuid);
            } else {
                c = GriefDefender.getCore().getClaim(null, name);
            }
            if (c == null) {
                return null;
            }
            final World claimWorld = Bukkit.getWorld(c.getWorldUniqueId());
            if (world.equals(claimWorld))
                return new GriefDefenderRegion(c);
            return null;
        } catch (final Throwable e) {
            return null;
        }
    }

    @Override
    public boolean hasMultipleOwners_i() {
        return false;
    }

    @Override
    protected Class<? extends Region> getRegionClass() {
        return GriefDefenderRegion.class;
    }
}
