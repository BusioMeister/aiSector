package ai.aisector.utils;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.io.BukkitObjectInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidObjectException;
import java.io.ObjectInputFilter;
import java.io.Serializable;
import java.util.Map;
import java.util.Set;

/** Fail-closed legacy compatibility reader. New item records use the versioned Bukkit byte format. */
final class LegacyItemInputStream extends BukkitObjectInputStream {
    private static final Set<String> CLASSES = Set.of(
            "java.lang.Object", "java.lang.String", "java.lang.Number", "java.lang.Integer", "java.lang.Long",
            "java.lang.Double", "java.lang.Float", "java.lang.Short", "java.lang.Byte", "java.lang.Boolean", "java.lang.Character",
            "java.util.UUID", "java.util.Map$Entry", "java.util.HashMap", "java.util.LinkedHashMap", "java.util.TreeMap",
            "java.util.ArrayList", "java.util.LinkedList", "java.util.HashSet", "java.util.LinkedHashSet", "java.util.TreeSet",
            "java.util.Arrays$ArrayList", "java.util.Collections$EmptyList", "java.util.Collections$EmptyMap",
            "java.util.Collections$EmptySet", "java.util.Collections$SingletonList", "java.util.Collections$SingletonMap",
            "java.util.Collections$SingletonSet", "org.bukkit.util.io.Wrapper",
            "com.google.common.collect.ImmutableMap$SerializedForm", "com.google.common.collect.RegularImmutableMap",
            "com.google.common.collect.ImmutableList$SerializedForm", "com.google.common.collect.RegularImmutableList",
            "com.google.common.collect.SingletonImmutableList", "com.google.common.collect.ImmutableSet$SerializedForm",
            "com.google.common.collect.RegularImmutableSet", "com.google.common.collect.SingletonImmutableSet",
            "com.google.common.collect.ImmutableBiMap$SerializedForm", "com.google.common.collect.RegularImmutableBiMap",
            "com.google.common.collect.SingletonImmutableBiMap");
    private static final Set<String> ALIASES = Set.of("ItemStack", "org.bukkit.inventory.ItemStack", "ItemMeta",
            "PlayerProfile", "org.bukkit.profile.PlayerProfile", "Vector", "org.bukkit.util.Vector", "BlockVector",
            "org.bukkit.util.BlockVector", "Color", "org.bukkit.Color", "FireworkEffect", "org.bukkit.FireworkEffect",
            "Pattern", "org.bukkit.block.banner.Pattern", "PotionEffect", "org.bukkit.potion.PotionEffect",
            "BoundingBox", "org.bukkit.util.BoundingBox", "Location", "org.bukkit.Location");
    private static final Set<String> VALUE_REPLACEMENTS = Set.of("org.bukkit.Color", "org.bukkit.FireworkEffect",
            "org.bukkit.util.Vector", "org.bukkit.util.BlockVector", "org.bukkit.util.BoundingBox", "org.bukkit.Location",
            "org.bukkit.block.banner.Pattern", "org.bukkit.potion.PotionEffect");

    LegacyItemInputStream(InputStream stream) throws IOException {
        super(stream);
        setObjectInputFilter(info -> {
            if (info.depth() > 32 || info.references() > 10_000 || info.streamBytes() > 262_144
                    || info.arrayLength() > 262_144) return ObjectInputFilter.Status.REJECTED;
            Class<?> type = info.serialClass();
            if (type == null) return ObjectInputFilter.Status.UNDECIDED;
            while (type.isArray()) type = type.getComponentType();
            if (type.isPrimitive() || CLASSES.contains(type.getName())) return ObjectInputFilter.Status.ALLOWED;
            // Bukkit's resolveObject can replace a safe Wrapper with a non-Java-serializable item/meta instance.
            if (!Serializable.class.isAssignableFrom(type)
                    && (ItemStack.class.isAssignableFrom(type) || ItemMeta.class.isAssignableFrom(type)
                    || org.bukkit.profile.PlayerProfile.class.isAssignableFrom(type) || VALUE_REPLACEMENTS.contains(type.getName())))
                return ObjectInputFilter.Status.ALLOWED;
            return ObjectInputFilter.Status.REJECTED;
        });
    }

    @Override protected Object resolveObject(Object object) throws IOException {
        if (object != null && object.getClass().getName().equals("org.bukkit.util.io.Wrapper")) {
            try {
                var field = object.getClass().getDeclaredField("map");
                field.setAccessible(true);
                Object map = field.get(object);
                if (!(map instanceof Map<?, ?> values)) throw new InvalidObjectException("Invalid legacy Bukkit wrapper");
                validateAlias(values.get("=="));
            } catch (ReflectiveOperationException | RuntimeException unsupportedWrapper) {
                throw new IOException("Unsupported legacy Bukkit wrapper; data retained", unsupportedWrapper);
            }
        }
        return super.resolveObject(object);
    }

    static void validateAlias(Object alias) throws InvalidObjectException {
        if (!(alias instanceof String name) || !ALIASES.contains(name))
            throw new InvalidObjectException("Unsupported legacy item alias; data retained");
    }
}
