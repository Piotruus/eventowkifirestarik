package pl.firestar.eventowki;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

/** Smoczy Totem, Excalibur, Plomienna Torba, Esencje (kowadlo), Koniczynka. */
public class Gear implements Listener {
    private final EventowkiPlugin pl;
    private final NamespacedKey critKey, fireKey, bagKey, killsKey, luckKey;
    private final Random rnd = new Random();

    static class BagHolder implements InventoryHolder {
        final int slot; Inventory inv;
        BagHolder(int slot) { this.slot = slot; }
        @Override public Inventory getInventory() { return inv; }
    }

    public Gear(EventowkiPlugin pl) {
        this.pl = pl;
        critKey = new NamespacedKey(pl, "esencja_krytyczna");
        fireKey = new NamespacedKey(pl, "esencja_ogien");
        bagKey = new NamespacedKey(pl, "torba_zawartosc");
        killsKey = new NamespacedKey(pl, "excalibur_ofiary");
        luckKey = new NamespacedKey(pl, "szczesciarz_procent");
        // ognioodpornosc ze spodni z esencja
        Bukkit.getScheduler().runTaskTimer(pl, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                ItemStack l = p.getInventory().getLeggings();
                if (l != null && l.hasItemMeta() && l.getItemMeta().getPersistentDataContainer().has(fireKey, PersistentDataType.BYTE))
                    p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 100, 0, true, false, true));
            }
        }, 40L, 40L);
    }

    public NamespacedKey critKey() { return critKey; }
    public NamespacedKey luckKey() { return luckKey; }

    public boolean hasKey(ItemStack it, NamespacedKey k) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(k, PersistentDataType.BYTE);
    }

    // ================= SMOCZY TOTEM =================
    @EventHandler
    public void onResurrect(EntityResurrectEvent e) {
        if (e.isCancelled() || !(e.getEntity() instanceof Player p)) return;
        EquipmentSlot hand = e.getHand();
        if (hand == null) return;
        if (!"smoczy_totem".equals(pl.items().idOf(p.getEquipment().getItem(hand)))) return;
        Bukkit.getScheduler().runTask(pl, () -> {
            p.setHealth(p.getAttribute(Attribute.MAX_HEALTH).getValue());
            p.sendActionBar(Items.color("&5&l✦ &dSmoczy Totem przywraca Ci pełne zdrowie!"));
        });
    }

    // ================= EXCALIBUR + TORBA (zabojstwa) =================
    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player v = e.getEntity();
        Player k = v.getKiller();
        if (k == null || k.equals(v)) return;
        excaliburKill(k, v);
        bagCollect(k, e);
    }

    private void excaliburKill(Player k, Player v) {
        ItemStack w = k.getInventory().getItemInMainHand();
        if (!"excalibur".equals(pl.items().idOf(w))) return;
        ItemMeta m = w.getItemMeta();
        String raw = m.getPersistentDataContainer().getOrDefault(killsKey, PersistentDataType.STRING, "");
        Set<String> set = new LinkedHashSet<>();
        if (!raw.isEmpty()) set.addAll(Arrays.asList(raw.split(",")));
        if (!set.add(v.getUniqueId().toString())) return;      // liczymy tylko ROZNYCH graczy
        m.getPersistentDataContainer().set(killsKey, PersistentDataType.STRING, String.join(",", set));
        int kills = set.size();
        if (kills >= 50) {
            Enchantment sharp = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("sharpness"));
            if (sharp != null) m.addEnchant(sharp, 7, true);
        }
        List<Component> exLore = pl.items().renderLore(pl.items().def("excalibur"), Items.excaliburPh(kills));
        Integer lp = m.getPersistentDataContainer().get(luckKey, PersistentDataType.INTEGER);
        if (lp != null) exLore.add(Items.color(luckLine(lp)));
        m.lore(exLore);
        w.setItemMeta(m);
        k.getInventory().setItemInMainHand(w);
        if (kills == 50) k.sendMessage(Items.color("&c&l» &7Excalibur ulepszony do &c12 obrażeń &6(Ostrość VII)&7!"));
    }

    // ================= PLOMIENNA TORBA =================
    private ItemStack[] readBag(ItemStack bag) {
        ItemStack[] out = new ItemStack[27];
        byte[] b = bag.getItemMeta().getPersistentDataContainer().get(bagKey, PersistentDataType.BYTE_ARRAY);
        if (b == null) return out;
        ItemStack[] des = ItemStack.deserializeItemsFromBytes(b);
        System.arraycopy(des, 0, out, 0, Math.min(27, des.length));
        return out;
    }

    private void writeBag(ItemStack bag, ItemStack[] contents) {
        ItemMeta m = bag.getItemMeta();
        m.getPersistentDataContainer().set(bagKey, PersistentDataType.BYTE_ARRAY, ItemStack.serializeItemsAsBytes(contents));
        bag.setItemMeta(m);
    }

    public void openBag(Player p) {
        ItemStack bag = p.getInventory().getItemInMainHand();
        BagHolder h = new BagHolder(p.getInventory().getHeldItemSlot());
        Inventory inv = Bukkit.createInventory(h, 27, Items.color("&c&l" + Items.smallCaps("plomienna torba")));
        h.inv = inv;
        inv.setContents(readBag(bag));
        p.openInventory(inv);
    }

    @EventHandler
    public void onBagClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof BagHolder h)) return;
        Player p = (Player) e.getPlayer();
        ItemStack bag = p.getInventory().getItem(h.slot);
        if (!"plomienna_torba".equals(pl.items().idOf(bag))) {
            for (ItemStack s : e.getInventory().getContents())
                if (s != null) p.getWorld().dropItemNaturally(p.getLocation(), s);
            return;
        }
        writeBag(bag, e.getInventory().getContents());
        p.getInventory().setItem(h.slot, bag);
    }

    @EventHandler
    public void onBagClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof BagHolder h)) return;
        if ("plomienna_torba".equals(pl.items().idOf(e.getCurrentItem()))
                || "plomienna_torba".equals(pl.items().idOf(e.getCursor()))
                || (e.getClick() == ClickType.NUMBER_KEY && e.getHotbarButton() == h.slot)
                || e.getClick() == ClickType.SWAP_OFFHAND)
            e.setCancelled(true);
    }

    private boolean valuable(ItemStack d) {
        if (pl.items().idOf(d) != null) return true;
        String n = d.getType().name();
        if (n.contains("NETHERITE") || n.contains("DIAMOND")) return true;
        return pl.getConfig().getStringList("settings.plomienna_torba.valuables").contains(n);
    }

    private void bagCollect(Player k, PlayerDeathEvent e) {
        PlayerInventory inv = k.getInventory();
        int slot = -1;
        for (int i = 0; i < inv.getSize(); i++)
            if ("plomienna_torba".equals(pl.items().idOf(inv.getItem(i)))) { slot = i; break; }
        if (slot < 0) return;
        ItemStack bag = inv.getItem(slot);
        Inventory tmp = Bukkit.createInventory(null, 27);
        tmp.setContents(readBag(bag));
        boolean changed = false;
        List<ItemStack> keep = new ArrayList<>();
        for (ItemStack d : e.getDrops()) {
            if (d == null || !valuable(d)) { keep.add(d); continue; }
            Map<Integer, ItemStack> left = tmp.addItem(d.clone());
            ItemStack rest = left.isEmpty() ? null : left.values().iterator().next();
            if (rest == null || rest.getAmount() < d.getAmount()) changed = true;
            if (rest != null) keep.add(rest);
        }
        if (!changed) return;
        e.getDrops().clear();
        e.getDrops().addAll(keep);
        writeBag(bag, tmp.getContents());
        inv.setItem(slot, bag);
        k.sendActionBar(Items.color("&c&lTorba &7przejęła cenne przedmioty z zabitego gracza!"));
    }

    // ================= ESENCJE (kowadlo) =================
    @EventHandler
    public void onAnvil(PrepareAnvilEvent e) {
        AnvilInventory inv = e.getInventory();
        ItemStack a = inv.getItem(0), b = inv.getItem(1);
        if (a == null || b == null) return;
        String bid = pl.items().idOf(b);
        if (bid == null) return;
        String type = a.getType().name();
        NamespacedKey key; String line;
        if (bid.equals("esencja_obrazen_krytycznych") && type.endsWith("_SWORD")) {
            key = critKey; line = "&d✦ Esencja Obrażeń Krytycznych";
        } else if (bid.equals("esencja_ogniodpornosci") && type.endsWith("_LEGGINGS")) {
            key = fireKey; line = "&6✦ Esencja Ogniodporności";
        } else return;
        if (hasKey(a, key)) return;
        ItemStack r = a.clone();
        ItemMeta m = r.getItemMeta();
        m.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        List<Component> lore = m.lore() != null ? new ArrayList<>(m.lore()) : new ArrayList<>();
        lore.add(Items.color(line));
        m.lore(lore);
        r.setItemMeta(m);
        e.setResult(r);
        e.getView().setRepairCost(5);
    }

    // ================= KONICZYNKA /szczesciarz =================
    static class LuckHolder implements InventoryHolder {
        Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    private int cfgI(String path, int def) { return pl.getConfig().getInt("settings." + path, def); }

    private static boolean isSword(ItemStack it) {
        return it != null && it.getType().name().endsWith("_SWORD");
    }

    /** Bonus obrazen w % zapisany na mieczu (null = brak). */
    public Integer luckPercent(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(luckKey, PersistentDataType.INTEGER);
    }

    private String luckLine(int pct) {
        return "&c⚠ &cZwiększone obrażenia o &f" + (pct >= 0 ? "+" : "") + pct + "%";
    }

    private int countClover(Player p) {
        int n = 0;
        for (ItemStack s : p.getInventory().getStorageContents())
            if ("koniczynka".equals(pl.items().idOf(s))) n += s.getAmount();
        return n;
    }

    private boolean takeClover(Player p, int amount) {
        if (countClover(p) < amount) return false;
        ItemStack[] c = p.getInventory().getStorageContents();
        for (int i = 0; i < c.length && amount > 0; i++) {
            if (!"koniczynka".equals(pl.items().idOf(c[i]))) continue;
            int take = Math.min(amount, c[i].getAmount());
            c[i].setAmount(c[i].getAmount() - take);
            amount -= take;
        }
        p.getInventory().setStorageContents(c);
        return true;
    }

    private ItemStack cloverButton(Player p) {
        ItemStack it = pl.items().create("koniczynka", 1);
        ItemMeta m = it.getItemMeta();
        m.getPersistentDataContainer().remove(pl.items().idKey());   // tylko przycisk, nie prawdziwy item
        m.displayName(Items.color("&a&lNałóż zaklęcie na miecz"));
        Integer cur = luckPercent(p.getInventory().getItemInMainHand());
        int min = cfgI("koniczynka.min-percent", -15), max = cfgI("koniczynka.max-percent", 55);
        List<Component> lore = new ArrayList<>();
        lore.add(Items.color(""));
        lore.add(Items.color(cur == null ? "&7Ten miecz nie ma jeszcze zaklęcia"
                : "&c⚠ &cPosiadasz zwiększone obrażenia o &f" + (cur >= 0 ? "+" : "") + cur + "%"));
        lore.add(Items.color(""));
        lore.add(Items.color("&8✕ &7Potrzebujesz: &aKONICZYNKA &8(&7masz: &e" + countClover(p) + "&8)"));
        lore.add(Items.color("&8✕ &7Zakres losowania: &c" + min + "% &7do &a+" + max + "%"));
        lore.add(Items.color(""));
        lore.add(Items.color("&a&lKliknij, &aaby nałożyć zaklęcie na miecz"));
        m.lore(lore);
        it.setItemMeta(m);
        return it;
    }

    private ItemStack pane(Material mat) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text(" "));
        it.setItemMeta(m);
        return it;
    }

    /** /szczesciarz: wymaga miecza w rece, koniczynki sa potrzebne dopiero przy losowaniu. */
    public void openLuck(Player p) {
        if (!isSword(p.getInventory().getItemInMainHand())) {
            p.sendMessage(Items.color("&cTrzymaj &7miecz &cw ręce, aby nałożyć zaklęcie!"));
            return;
        }
        LuckHolder h = new LuckHolder();
        Inventory inv = Bukkit.createInventory(h, 27, Items.color("&aSzczęściarz"));
        h.inv = inv;
        Material Y = Material.YELLOW_STAINED_GLASS_PANE, O = Material.ORANGE_STAINED_GLASS_PANE,
                L = Material.LIGHT_GRAY_STAINED_GLASS_PANE, G = Material.GRAY_STAINED_GLASS_PANE;
        Material[] layout = {
                Y, O, L, L, G, L, L, O, Y,
                O, G, G, G, G, G, G, G, O,
                Y, O, L, L, G, L, L, O, Y};
        for (int i = 0; i < 27; i++) inv.setItem(i, pane(layout[i]));
        inv.setItem(13, cloverButton(p));
        p.openInventory(inv);
    }

    @EventHandler
    public void onLuckClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof LuckHolder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getRawSlot() != 13) return;

        ItemStack sword = p.getInventory().getItemInMainHand();
        if (!isSword(sword)) {
            p.sendMessage(Items.color("&cMusisz trzymać miecz w ręce!"));
            p.closeInventory();
            return;
        }
        if (!takeClover(p, Math.max(1, cfgI("koniczynka.cost", 1)))) {
            p.sendActionBar(Items.color("&cNie masz &aKoniczynki &cw ekwipunku!"));
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        int min = cfgI("koniczynka.min-percent", -15), max = cfgI("koniczynka.max-percent", 55);
        if (min > max) { int t = min; min = max; max = t; }
        int pct = min + rnd.nextInt(max - min + 1);

        ItemMeta m = sword.getItemMeta();
        m.getPersistentDataContainer().set(luckKey, PersistentDataType.INTEGER, pct);
        List<Component> lore = m.lore() != null ? new ArrayList<>(m.lore()) : new ArrayList<>();
        lore.removeIf(c -> PlainTextComponentSerializer.plainText().serialize(c).contains("Zwiększone obrażenia o"));
        lore.add(Items.color(luckLine(pct)));
        m.lore(lore);
        sword.setItemMeta(m);
        p.getInventory().setItemInMainHand(sword);

        e.getInventory().setItem(13, cloverButton(p));
        p.sendActionBar(Items.color((pct >= 0 ? "&a&l☘ Wylosowano: &a+" : "&c&l☘ Wylosowano: &c") + pct + "% &7obrażeń"));
        p.playSound(p.getLocation(), pct >= 0 ? org.bukkit.Sound.ENTITY_PLAYER_LEVELUP : org.bukkit.Sound.BLOCK_ANVIL_LAND,
                1f, pct >= 0 ? 1.4f : 0.8f);
    }

    @EventHandler
    public void onLuckDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof LuckHolder) e.setCancelled(true);
    }
}
