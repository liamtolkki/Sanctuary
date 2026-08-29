package dev.liamtolkkinen.sanctuary.advancement;

import dev.liamtolkkinen.extendeditems.ExtendedItemId;
import dev.liamtolkkinen.extendeditems.ExtendedItemIds;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.Material;

public final class SanctuaryAdvancementCatalog {
    public enum Frame {
        TASK("task"),
        GOAL("goal"),
        CHALLENGE("challenge");

        private final String jsonName;

        Frame(String jsonName) {
            this.jsonName = jsonName;
        }

        public String jsonName() {
            return jsonName;
        }
    }

    public record Definition(
        String key,
        String parentKey,
        Material icon,
        String title,
        String description,
        Frame frame,
        boolean announceToChat,
        List<String> criteria
    ) {
        public Definition {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("Advancement key must not be blank");
            }
            if (parentKey != null && parentKey.isBlank()) {
                throw new IllegalArgumentException("Advancement parent key must not be blank");
            }
            Objects.requireNonNull(icon, "icon");
            if (title == null || title.isBlank()) {
                throw new IllegalArgumentException("Advancement title must not be blank");
            }
            if (description == null || description.isBlank()) {
                throw new IllegalArgumentException("Advancement description must not be blank");
            }
            Objects.requireNonNull(frame, "frame");
            criteria = List.copyOf(Objects.requireNonNull(criteria, "criteria"));
            if (criteria.isEmpty()
                || criteria.stream().anyMatch(value -> value == null || value.isBlank())
                || criteria.stream().distinct().count() != criteria.size()) {
                throw new IllegalArgumentException(
                    "Advancement criteria must be non-blank and unique"
                );
            }
        }
    }

    public static final String FIRST_FRAGMENT = "first_fragment";
    public static final String FIRST_SHARD = "first_shard";
    public static final String DIVINE_ALTAR = "divine_altar";
    public static final String SANCTUARY_BEACON = "sanctuary_beacon";
    public static final String FIRST_ARTIFACT = "first_artifact";
    public static final String MASTER_ARTIFICER = "master_artificer";
    public static final String SANCTUARY_CONDUIT = "sanctuary_conduit";
    public static final String FIRST_COMPANION = "first_companion";
    public static final String WARDEN_COMPANION = "warden_companion";
    public static final String WITHER_COMPANION = "wither_companion";
    public static final String FIRST_SENTRY = "first_sentry";
    public static final String SANCTUARY_EXTENDED = "sanctuary_extended";
    public static final String TIER_FIVE_ANCHOR = "tier_five_anchor";
    public static final String WHAT_A_WASTE = "what_a_waste";
    public static final String FIRST_OFFERING = "first_offering";
    public static final String HALF_OFFERINGS = "half_offerings";
    public static final String ALL_OFFERINGS = "all_offerings";
    public static final String DIVINE_RELIC = "divine_relic";

    private static final String COMPLETE = "complete";

    private static final Map<ExtendedItemId, String> MASTER_ARTIFACT_CRITERIA =
        Map.ofEntries(
            Map.entry(ExtendedItemIds.SANCTUARY_CORE, "sanctuary_core"),
            Map.entry(ExtendedItemIds.TERRITORY_KEYSTONE, "territory_keystone"),
            Map.entry(ExtendedItemIds.WATCHERS_EYE, "watchers_eye"),
            Map.entry(ExtendedItemIds.ATTUNEMENT_RELIC, "attunement_relic"),
            Map.entry(ExtendedItemIds.CONSECRATED_KEYSTONE, "consecrated_keystone")
        );

    private static final List<Definition> DEFINITIONS = List.of(
        d(FIRST_FRAGMENT, null, Material.SMALL_AMETHYST_BUD, "A Spark of Consecration",
            "Discover a Consecrated Shard Fragment.", Frame.TASK, true),
        d(FIRST_SHARD, FIRST_FRAGMENT, Material.AMETHYST_SHARD, "Made Whole",
            "Combine four fragments into a Consecrated Shard.", Frame.TASK, true),
        d(DIVINE_ALTAR, FIRST_SHARD, Material.LECTERN, "An Audience with the Divine",
            "Craft a Divine Altar.", Frame.GOAL, true),
        d(SANCTUARY_BEACON, FIRST_SHARD, Material.RESPAWN_ANCHOR, "Light the Sanctuary",
            "Craft or place your first Sanctuary anchor.", Frame.GOAL, true),
        d(FIRST_ARTIFACT, FIRST_SHARD, Material.ECHO_SHARD, "Arcane Artificer",
            "Craft your first Sanctuary progression artifact.", Frame.TASK, true),
        new Definition(
            MASTER_ARTIFICER,
            FIRST_ARTIFACT,
            Material.RESPAWN_ANCHOR,
            "Master of the Sacred Arts",
            "Craft every major Sanctuary progression artifact.",
            Frame.CHALLENGE,
            true,
            List.copyOf(MASTER_ARTIFACT_CRITERIA.values())
        ),
        d(SANCTUARY_CONDUIT, SANCTUARY_BEACON, Material.CONDUIT, "Sanctuary Below",
            "Craft a Sanctuary Conduit anchor.", Frame.GOAL, true),
        d(SANCTUARY_EXTENDED, SANCTUARY_BEACON, Material.LODESTONE, "Beyond the First Light",
            "Extend a Sanctuary with another anchor.", Frame.GOAL, true),
        d(TIER_FIVE_ANCHOR, SANCTUARY_BEACON, Material.RESPAWN_ANCHOR, "A Sanctuary Perfected",
            "Raise a Sanctuary anchor to Tier V.", Frame.CHALLENGE, true),
        d(WHAT_A_WASTE, TIER_FIVE_ANCHOR, Material.FIRE_CHARGE, "What a Waste",
            "Permanently destroy a Tier V Sanctuary anchor.", Frame.CHALLENGE, true),
        d(FIRST_COMPANION, DIVINE_ALTAR, Material.IRON_GOLEM_SPAWN_EGG, "A Faithful Companion",
            "Obtain your first Companion Egg.", Frame.TASK, true),
        d(WARDEN_COMPANION, FIRST_COMPANION, Material.WARDEN_SPAWN_EGG, "The Deep Answers",
            "Obtain a Warden Companion Egg.", Frame.CHALLENGE, true),
        d(WITHER_COMPANION, FIRST_COMPANION, Material.WITHER_SPAWN_EGG, "Death, Bound",
            "Obtain a Wither Companion Egg.", Frame.CHALLENGE, true),
        d(FIRST_SENTRY, FIRST_COMPANION, Material.ARMOR_STAND, "Standing Guard",
            "Craft your first Sentry Post.", Frame.TASK, true),
        d(FIRST_OFFERING, DIVINE_ALTAR, Material.GOLD_INGOT, "The First Offering",
            "Complete the first sacred offering.", Frame.TASK, true),
        d(HALF_OFFERINGS, FIRST_OFFERING, Material.EXPERIENCE_BOTTLE, "Halfway to Grace",
            "Complete six of the twelve sacred offerings.", Frame.GOAL, true),
        d(ALL_OFFERINGS, HALF_OFFERINGS, Material.NETHER_STAR, "The Final Offering",
            "Complete all twelve sacred offerings and earn divine favor.", Frame.CHALLENGE, true),
        d(DIVINE_RELIC, ALL_OFFERINGS, Material.TOTEM_OF_UNDYING, "Divine Favor",
            "Receive your first Divine Relic and unlock its altar recipe.", Frame.CHALLENGE, true)
    );

    private static final Map<String, Definition> BY_KEY;

    static {
        Map<String, Definition> definitions = new LinkedHashMap<>();
        for (Definition definition : DEFINITIONS) {
            if (definitions.put(definition.key(), definition) != null) {
                throw new IllegalStateException(
                    "Duplicate Sanctuary advancement key " + definition.key()
                );
            }
            if (definition.parentKey() != null
                && !definitions.containsKey(definition.parentKey())) {
                throw new IllegalStateException(
                    "Advancement " + definition.key() + " must be declared after parent "
                        + definition.parentKey()
                );
            }
        }
        BY_KEY = Map.copyOf(definitions);
    }

    private SanctuaryAdvancementCatalog() {
    }

    public static List<Definition> definitions() {
        return DEFINITIONS;
    }

    public static Optional<Definition> find(String key) {
        return key == null ? Optional.empty() : Optional.ofNullable(BY_KEY.get(key));
    }

    public static Optional<String> masterArtifactCriterion(ExtendedItemId itemId) {
        return itemId == null
            ? Optional.empty()
            : Optional.ofNullable(MASTER_ARTIFACT_CRITERIA.get(itemId));
    }

    public static Map<ExtendedItemId, String> masterArtifactCriteria() {
        return MASTER_ARTIFACT_CRITERIA;
    }

    public static String completionCriterion() {
        return COMPLETE;
    }

    private static Definition d(
        String key,
        String parent,
        Material icon,
        String title,
        String description,
        Frame frame,
        boolean announce
    ) {
        return new Definition(
            key,
            parent,
            icon,
            title,
            description,
            frame,
            announce,
            List.of(COMPLETE)
        );
    }
}
