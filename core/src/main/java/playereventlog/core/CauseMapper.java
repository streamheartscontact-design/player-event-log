package playereventlog.core;

import java.util.HashMap;
import java.util.Map;

public final class CauseMapper {
    public static final String MOB = "mob";
    public static final String PLAYER = "player";
    public static final String PROJECTILE = "projectile";
    public static final String FALL = "fall";
    public static final String FIRE = "fire";
    public static final String LAVA = "lava";
    public static final String HOT_FLOOR = "hot_floor";
    public static final String DROWN = "drown";
    public static final String EXPLOSION = "explosion";
    public static final String CACTUS = "cactus";
    public static final String SWEET_BERRY = "sweet_berry";
    public static final String SUFFOCATION = "suffocation";
    public static final String FREEZE = "freeze";
    public static final String MAGIC = "magic";
    public static final String POISON = "poison";
    public static final String WITHER = "wither";
    public static final String VOID = "void";
    public static final String STARVE = "starve";
    public static final String LIGHTNING = "lightning";
    public static final String FALLING_BLOCK = "falling_block";
    public static final String FLY_INTO_WALL = "fly_into_wall";
    public static final String THORNS = "thorns";
    public static final String WORLD_BORDER = "world_border";
    public static final String KILL = "kill";
    public static final String GENERIC = "generic";
    public static final String UNKNOWN = "unknown";

    private static final Map<String, String> MAP = new HashMap<String, String>();

    static {
        m(FIRE, "in_fire", "campfire", "on_fire");
        m(LAVA, "lava");
        m(HOT_FLOOR, "hot_floor");
        m(LIGHTNING, "lightning_bolt");
        m(SUFFOCATION, "in_wall", "cramming");
        m(DROWN, "drown");
        m(STARVE, "starve");
        m(CACTUS, "cactus");
        m(FALL, "fall", "ender_pearl", "stalagmite");
        m(FLY_INTO_WALL, "fly_into_wall");
        m(VOID, "out_of_world", "fell_out_of_world");
        m(GENERIC, "generic", "dry_out", "dryout");
        m(KILL, "generic_kill");
        m(MAGIC, "magic", "indirect_magic", "dragon_breath", "even_more_magic");
        m(WITHER, "wither");
        m(FALLING_BLOCK, "falling_block", "anvil", "falling_anvil", "falling_stalactite");
        m(SWEET_BERRY, "sweet_berry_bush");
        m(FREEZE, "freeze");
        m(MOB, "mob", "mob_attack", "mob_attack_no_aggro", "sting", "sonic_boom");
        m(PLAYER, "player", "player_attack");
        m(PROJECTILE, "arrow", "trident", "mob_projectile", "spit", "fireball", "unattributed_fireball",
                "wither_skull", "thrown", "wind_charge");
        m(EXPLOSION, "explosion", "player_explosion", "explosion.player", "fireworks", "bad_respawn_point");
        m(THORNS, "thorns");
        m(WORLD_BORDER, "outside_border");
    }

    private static void m(String cause, String... keys) {
        for (String k : keys) MAP.put(k, cause);
    }

    private CauseMapper() {
    }

    public static String normalize(String id) {
        if (id == null) return null;
        int colon = id.indexOf(':');
        String s = colon >= 0 ? id.substring(colon + 1) : id;
        StringBuilder sb = new StringBuilder(s.length() + 4);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isUpperCase(c)) {
                if (sb.length() > 0) sb.append('_');
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String fromId(String id) {
        String n = normalize(id);
        if (n == null) return null;
        String c = MAP.get(n);
        if (c == null && n.endsWith(".player")) c = MAP.get(n.substring(0, n.length() - ".player".length()));
        if (c == null && n.endsWith(".item")) c = MAP.get(n.substring(0, n.length() - ".item".length()));
        return c;
    }

    public static String fromDamage(String typeId, String msgId, String attacker) {
        String c = fromId(typeId);
        if (c == null) c = fromId(msgId);
        if (c == null) c = attacker != null ? MOB : UNKNOWN;
        if ((MOB.equals(c) || GENERIC.equals(c)) && "minecraft:player".equals(attacker)) c = PLAYER;
        return c;
    }

    public static String fromDeathKey(String key) {
        if (key == null) return null;
        if (key.startsWith("death.fell.")) return FALL;
        if (!key.startsWith("death.attack.")) return null;
        String rest = key.substring("death.attack.".length());
        if (rest.startsWith("badRespawnPoint")) return EXPLOSION;
        if (rest.startsWith("explosion")) return EXPLOSION;
        int dot = rest.indexOf('.');
        String id = dot >= 0 ? rest.substring(0, dot) : rest;
        String c = fromId(id);
        return c != null ? c : UNKNOWN;
    }
}
