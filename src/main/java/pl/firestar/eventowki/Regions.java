package pl.firestar.eventowki;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Ograniczenia regionow WorldGuard:
 *  BLOCKED (spawn, endspawn, netherspawn) - eventowki nie dzialaja,
 *  PVP (pvp, endpvp, netherpvp)           - dzialaja, ale bomba nie niszczy blokow.
 */
public class Regions {
    public enum State { NORMAL, PVP, BLOCKED }

    private final EventowkiPlugin pl;
    private boolean wg;
    private Set<String> blocked = new HashSet<>(), pvp = new HashSet<>(),
            allowed = new HashSet<>(), noDamage = new HashSet<>();
    private final Map<UUID, Long> throttle = new HashMap<>();

    public Regions(EventowkiPlugin pl) {
        this.pl = pl;
        reload();
    }

    private static Set<String> lower(List<String> l) {
        Set<String> s = new HashSet<>();
        for (String x : l) s.add(x.toLowerCase());
        return s;
    }

    public void reload() {
        FileConfiguration c = pl.getConfig();
        boolean want = c.getBoolean("worldguard.enabled", true);
        wg = want && Bukkit.getPluginManager().getPlugin("WorldGuard") != null;
        if (want && !wg) pl.getLogger().warning("Nie znaleziono WorldGuard - ograniczenia regionow sa wylaczone.");
        blocked = lower(c.getStringList("worldguard.blocked-regions"));
        pvp = lower(c.getStringList("worldguard.pvp-regions"));
        allowed = lower(c.getStringList("worldguard.allowed-in-blocked"));
        noDamage = lower(c.getStringList("worldguard.pvp-no-block-damage"));
    }

    public State state(Location loc) {
        if (!wg || loc == null || loc.getWorld() == null) return State.NORMAL;
        Set<String> ids;
        try {
            ids = WgHook.regionIds(loc);
        } catch (Throwable t) {
            return State.NORMAL;
        }
        State st = State.NORMAL;
        for (String id : ids) {
            if (blocked.contains(id)) return State.BLOCKED;      // BLOCKED ma pierwszenstwo
            if (pvp.contains(id)) st = State.PVP;
        }
        return st;
    }

    /** true = gracz moze uzyc eventowki tutaj; w razie zakazu wysyla wiadomosc (max co 2s). */
    public boolean canUse(Player p, String id) {
        if (allowed.contains(id)) return true;
        if (state(p.getLocation()) != State.BLOCKED) return true;
        long now = System.currentTimeMillis();
        Long last = throttle.get(p.getUniqueId());
        if (last == null || now - last > 2000) {
            throttle.put(p.getUniqueId(), now);
            p.sendMessage(Items.color(pl.getConfig().getString("worldguard.message",
                    "&cW tym obrębie nie możesz używać eventówek!")));
        }
        return false;
    }

    /** true = w tym miejscu przedmiot ma nie niszczyc blokow (region PVP). */
    public boolean noBlockDamage(Location l, String id) {
        return state(l) == State.PVP && noDamage.contains(id.toLowerCase());
    }
}
