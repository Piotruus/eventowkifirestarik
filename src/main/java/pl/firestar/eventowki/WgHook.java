package pl.firestar.eventowki;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Location;

import java.util.HashSet;
import java.util.Set;

/** Jedyne miejsce, ktore dotyka klas WorldGuarda (wolane tylko gdy WG jest na serwerze). */
final class WgHook {
    private WgHook() {}

    static Set<String> regionIds(Location loc) {
        RegionManager m = WorldGuard.getInstance().getPlatform().getRegionContainer()
                .get(BukkitAdapter.adapt(loc.getWorld()));
        Set<String> out = new HashSet<>();
        if (m == null) return out;
        ApplicableRegionSet set = m.getApplicableRegions(BukkitAdapter.asBlockVector(loc));
        for (ProtectedRegion r : set) out.add(r.getId().toLowerCase());
        return out;
    }
}
