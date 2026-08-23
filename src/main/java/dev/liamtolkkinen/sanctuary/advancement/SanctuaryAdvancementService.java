package dev.liamtolkkinen.sanctuary.advancement;

import dev.liamtolkkinen.extendeditems.ExtendedItemId;
import dev.liamtolkkinen.extendeditems.ExtendedItemIds;
import dev.liamtolkkinen.extendeditems.ExtendedItems;
import dev.liamtolkkinen.sanctuary.crafting.SanctuaryRecipeCatalog;
import dev.liamtolkkinen.sanctuary.sentry.SentryRecipeCatalog;
import java.util.Locale;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.plugin.java.JavaPlugin;

public final class SanctuaryAdvancementService implements Listener {
    private static final String ROOT_BACKGROUND="minecraft:gui/advancements/backgrounds/stone";
    private final JavaPlugin plugin; private final String namespace;
    public SanctuaryAdvancementService(JavaPlugin plugin){this.plugin=Objects.requireNonNull(plugin,"plugin");this.namespace=plugin.getName().toLowerCase(Locale.ROOT);}
    public void start(){registerAdvancements();plugin.getServer().getPluginManager().registerEvents(this,plugin);for(Player player:plugin.getServer().getOnlinePlayers())refreshPossessionMilestones(player);}
    public void recordOfferingProgress(Player player,int completedOfferings){Objects.requireNonNull(player,"player");if(completedOfferings<0||completedOfferings>12)throw new IllegalArgumentException("completedOfferings must be between 0 and 12");if(completedOfferings>=1)grantCompleted(player,SanctuaryAdvancementCatalog.FIRST_OFFERING);if(completedOfferings>=6)grantCompleted(player,SanctuaryAdvancementCatalog.HALF_OFFERINGS);if(completedOfferings>=12)grantCompleted(player,SanctuaryAdvancementCatalog.ALL_OFFERINGS);}
    public void recordDivineRelicReceived(Player player){Objects.requireNonNull(player,"player");grantCompleted(player,SanctuaryAdvancementCatalog.DIVINE_RELIC);}

    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void onCraft(CraftItemEvent event){if(!(event.getWhoClicked() instanceof Player player))return;String recipeKey=recipeKey(event.getRecipe());if(recipeKey==null)return;SanctuaryRecipeCatalog.findByKey(recipeKey).ifPresent(definition->recordSanctuaryCraft(player,definition.result()));if(SentryRecipeCatalog.sentryConversions().stream().anyMatch(conversion->conversion.key().equals(recipeKey)))grantCompleted(player,SanctuaryAdvancementCatalog.FIRST_SENTRY);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void onBlockPlace(BlockPlaceEvent event){if(ExtendedItems.is(event.getItemInHand(),ExtendedItemIds.SANCTUARY_BEACON))grantCompleted(event.getPlayer(),SanctuaryAdvancementCatalog.SANCTUARY_BEACON);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void onItemPickup(EntityPickupItemEvent event){if(event.getEntity() instanceof Player player)observePossession(player,event.getItem().getItemStack());}
    @EventHandler(priority=EventPriority.MONITOR) public void onPlayerJoin(PlayerJoinEvent event){scheduleRefresh(event.getPlayer());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void onInventoryClick(InventoryClickEvent event){if(event.getWhoClicked() instanceof Player player)scheduleRefresh(player);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void onInventoryDrag(InventoryDragEvent event){if(event.getWhoClicked() instanceof Player player)scheduleRefresh(player);}

    private void recordSanctuaryCraft(Player player,ExtendedItemId result){
        if(result.equals(ExtendedItemIds.CONSECRATED_SHARD)){grantCompleted(player,SanctuaryAdvancementCatalog.FIRST_FRAGMENT);grantCompleted(player,SanctuaryAdvancementCatalog.FIRST_SHARD);return;}
        if(result.equals(ExtendedItemIds.DIVINE_ALTAR)){grantCompleted(player,SanctuaryAdvancementCatalog.DIVINE_ALTAR);return;}
        if(result.equals(ExtendedItemIds.SANCTUARY_BEACON)){grantCompleted(player,SanctuaryAdvancementCatalog.SANCTUARY_BEACON);return;}
        if(result.equals(ExtendedItemIds.SANCTUARY_CONDUIT)){grantCompleted(player,SanctuaryAdvancementCatalog.SANCTUARY_CONDUIT);return;}
        SanctuaryAdvancementCatalog.masterArtifactCriterion(result).ifPresent(criterion->{grantCompleted(player,SanctuaryAdvancementCatalog.FIRST_ARTIFACT);grantCriterion(player,SanctuaryAdvancementCatalog.MASTER_ARTIFICER,criterion);});
    }
    private void refreshPossessionMilestones(Player player){for(ItemStack item:player.getInventory().getContents())observePossession(player,item);}
    private void observePossession(Player player,ItemStack item){if(item==null||item.getType().isAir())return;if(ExtendedItems.is(item,ExtendedItemIds.CONSECRATED_SHARD_FRAGMENT))grantCompleted(player,SanctuaryAdvancementCatalog.FIRST_FRAGMENT);if(ExtendedItems.is(item,ExtendedItemIds.SANCTUARY_BEACON))grantCompleted(player,SanctuaryAdvancementCatalog.SANCTUARY_BEACON);if(ExtendedItems.is(item,ExtendedItemIds.DIVINE_RELIC))grantCompleted(player,SanctuaryAdvancementCatalog.DIVINE_RELIC);}
    private void scheduleRefresh(Player player){plugin.getServer().getScheduler().runTask(plugin,()->{if(player.isOnline())refreshPossessionMilestones(player);});}
    private String recipeKey(Recipe recipe){if(!(recipe instanceof Keyed keyed))return null;NamespacedKey key=keyed.getKey();return key.getNamespace().equals(namespace)?key.getKey():null;}
    private void grantCompleted(Player player,String advancementKey){var definition=SanctuaryAdvancementCatalog.find(advancementKey).orElseThrow();for(String criterion:definition.criteria())grantCriterion(player,advancementKey,criterion);}
    private void grantCriterion(Player player,String advancementKey,String criterion){Advancement advancement=Bukkit.getAdvancement(key(advancementKey));if(advancement==null)throw new IllegalStateException("Sanctuary advancement is not loaded: "+advancementKey);AdvancementProgress progress=player.getAdvancementProgress(advancement);if(!progress.getAwardedCriteria().contains(criterion))progress.awardCriteria(criterion);}
    @SuppressWarnings("deprecation") private void registerAdvancements(){for(var definition:SanctuaryAdvancementCatalog.definitions()){NamespacedKey key=key(definition.key());if(Bukkit.getAdvancement(key)!=null)continue;Advancement loaded=Bukkit.getUnsafe().loadAdvancement(key,toJson(definition));if(loaded==null)throw new IllegalStateException("Failed to load Sanctuary advancement "+key);}}
    private String toJson(SanctuaryAdvancementCatalog.Definition d){StringBuilder j=new StringBuilder("{");if(d.parentKey()!=null)j.append("\"parent\":\"").append(key(d.parentKey())).append("\",");j.append("\"display\":{\"icon\":{\"id\":\"").append(d.icon().getKey()).append("\"},\"title\":{\"text\":\"").append(escapeJson(d.title())).append("\"},\"description\":{\"text\":\"").append(escapeJson(d.description())).append("\"},\"frame\":\"").append(d.frame().jsonName()).append("\",\"show_toast\":true,\"announce_to_chat\":").append(d.announceToChat()).append(",\"hidden\":false");if(d.parentKey()==null)j.append(",\"background\":\"").append(ROOT_BACKGROUND).append("\"");j.append("},\"criteria\":{");for(int i=0;i<d.criteria().size();i++){if(i>0)j.append(',');j.append('"').append(escapeJson(d.criteria().get(i))).append("\":{\"trigger\":\"minecraft:impossible\"}");}j.append("},\"requirements\":[");for(int i=0;i<d.criteria().size();i++){if(i>0)j.append(',');j.append("[\"").append(escapeJson(d.criteria().get(i))).append("\"]");}return j.append("],\"sends_telemetry_event\":false}").toString();}
    private NamespacedKey key(String value){return new NamespacedKey(plugin,value);} private String escapeJson(String value){return value.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r").replace("\t","\\t");}
}
