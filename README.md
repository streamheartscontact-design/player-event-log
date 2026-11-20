# Player Event Log

A free, open-source client-side mod for Minecraft: Java Edition. It records what happens to your own player (damage, death, healing, effects, water, lava, fire, freezing, dimension changes, walking, jumping, sneaking, swinging, mining) to a local file, `logs/player-events.jsonl`, one JSON object per line.

- Read only. The mod observes the client and never changes gameplay. It gives no in-game advantage.
- Writes one local file. No network code, no open ports, no telemetry.
- Works in singleplayer and on servers (for your own player). Client only: nothing needs to be installed on the server, and you can join servers without this mod or with other mods (no network channels; the Forge and NeoForge builds are marked so the server's mod check ignores them). If a server's rules forbid client mods, follow those rules.
- The format is open and documented below, so anyone can write a program that reads the file (stream overlays, statistics, notifications, accessibility tools and so on).
- No payments, purchase checks, paid unlocks or play-to-earn rewards. Not tied to any particular program. Contains no Mojang textures, sounds or code.

NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT

License: MIT (see `LICENSE`), Copyright (c) 2026 Stream Hearts
Contact: streamhearts.contact@gmail.com

## 한국어 요약

마인크래프트 자바 에디션용 무료·오픈소스 클라이언트 모드입니다(서버에 설치할 필요 없음, 서버 규칙이 클라이언트 모드를 금지하면 따를 것). 내 플레이어의 상태 변화와 움직임(피격·사망·회복·효과·물·용암·불·얼어붙음·차원 이동·걷기·점프·웅크리기·휘두르기·블록 캐기)을 게임 폴더의 `logs/player-events.jsonl`에 한 줄에 JSON 하나씩 기록합니다. 게임을 바꾸지 않고 읽기만 하며, 네트워크를 쓰지 않고 게임 속 이득도 없습니다. 형식은 아래에 공개되어 있어 누구나 이 파일을 읽는 프로그램을 만들 수 있습니다. 문의: streamhearts.contact@gmail.com

## Supported versions

| Loader | Minecraft | Java | Jar |
|---|---|---|---|
| Fabric | 1.16.5, 1.18.2, 1.20, 1.20.1, 1.21, 1.21.1, 1.21.11, 26.3 | whatever that version uses (8 / 17 / 21 / 25) | `player-event-log-1.4.0+fabric.jar` (one file) |
| Forge | 1.12.2 | 8 | `player-event-log-1.4.0+forge-1.12.2.jar` |
| Forge | 1.20.1 | 17 | `player-event-log-1.4.0+forge-1.20.1.jar` |
| NeoForge | 1.21.1 | 21 | `player-event-log-1.4.0+neoforge-1.21.1.jar` |

Other loader and version combinations are not supported.

One Fabric jar works on every Fabric version above, so several game versions can share one `mods` folder. It only needs Fabric Loader 0.14.0 or newer (Fabric API is not required). On a Fabric version that is not listed, the game starts normally and the log shows one line, `[PlayerEventLog] Minecraft <version> is not supported by this version: nothing is recorded`.

## Installation

1. Fabric: take `player-event-log-1.4.0+fabric.jar`. Forge or NeoForge: take the jar for your Minecraft version.
2. Put it in the `mods` folder of your game directory and start the game. Remove older per-version Fabric jars (`...+fabric-1.20.1.jar` and so on) if you have them.
3. After you join a world, `logs/player-events.jsonl` appears in the game directory.

## Privacy

- Nothing that can contain a name is written unless you turn on `include_names` (off by default): not your player name, not the names of entities that hit you, and not the death message text (which names players). Causes are still written as ids (`cause`, `message_key`, `attacker` type).
- Coordinates, block types, item names and chat are never written. Movement lines only say what kind of action happened.
- The file stays on your computer. The mod sends nothing anywhere.

## Output file

- Location: `<game directory>/logs/player-events.jsonl` (configurable)
- Format: UTF-8, one JSON object per line, lines end with `\n`
- Size: above 10 MB the current file is renamed to `player-events.jsonl.1` (replacing an older `.1`) and a new file is started.
- Writing: the game tick only queues strings; a background thread writes them to the file.
- A value that stays the same is not written again. A line is written only when something changes.

### Common header (every line)

| Field | Type | Meaning |
|---|---|---|
| `v` | integer | Format version, currently `1`. Raised only when the meaning of an existing field changes. New events and fields keep `1`. |
| `t` | string | Time of the line, ISO 8601 with offset (`2026-11-20T17:01:27.512+09:00`) |
| `mc` | string | Minecraft version (`1.20.1`, `26.3`, ...) |
| `loader` | string | `fabric`, `forge` or `neoforge` |
| `event` | string | Event name (tables below) |
| `kind` | string | `state` = a line with current values, `event` = something that just happened |

Example:

```json
{"v":1,"t":"2026-11-20T17:01:32.130+09:00","mc":"1.20.1","loader":"fabric","event":"hurt","kind":"event","amount":3.0,"health":17.0,"max_health":20.0,"absorption":0.0,"cause":"fall","damage_type":"minecraft:fall","attacker":null,"attacker_name":null,"direct":null,"estimated":false}
```

Notes for readers

- Skip events and fields you do not know. More may be added later.
- A value a version cannot provide is written as `null`; the field is not left out. Exception: `message` in `death` is absent while `include_names` is off.
- The mod makes no judgements such as "low health". Use the raw values (`health`, `max_health`, ...) and decide in your program.
- Every game start writes a `session_start` line first. After the file is rotated, read the new file from the beginning.
- Numbers such as health are rounded to two decimals. Health is counted in half hearts (one half heart = 1.0).

## State lines (`kind: "state"`)

| Event | When | Fields |
|---|---|---|
| `world_join` | After joining a world, once the values have settled (about 2 s) | `singleplayer`, `player` (name, only with `include_names`), `dimension`, `gamemode`, `health`, `max_health`, `absorption`, `food`, `saturation`, `armor`, `air`, `max_air`, `xp_level`, `effects`[] |
| `state` | When any of these values changed (merged, at most every 0.5 s) | `health`, `max_health`, `absorption`, `food`, `max_food` (20), `saturation`, `armor`, `air`, `max_air`, `xp_level`, `dimension`, `gamemode`, `alive`, `in_water`, `submerged`, `in_lava`, `on_fire`, `frozen_ticks`, `fully_frozen`, `sleeping`, `elytra`, `swimming`, `sprinting`, `vehicle`, `moving` (same rule as `move_start`), `sneaking`, `on_ground`, `effects`[] |
| `max_health` | Maximum health changed | `max_health`, `delta`, `health` |
| `absorption` | Absorption (yellow hearts) changed; losses from a hit are part of `hurt` | `absorption`, `delta` |
| `food` | Food level changed, or saturation changed by 1 or more | `food`, `max_food`, `saturation`, `delta` |
| `armor` | Armor value changed | `armor`, `delta` |
| `air` | Number of air bubbles (0-10) changed | `air` (ticks, usually up to 300), `max_air`, `bubbles` |

`effects[]` entries: `effect` (id, e.g. `minecraft:speed`), `amplifier` (from 0), `level` (from 1), `duration` (ticks, -1 = infinite), `seconds` (null when infinite), `infinite`, `ambient` (beacon and similar), `category` (`beneficial` / `harmful` / `neutral`).

## Event lines (`kind: "event"`)

| Event | Meaning | Fields |
|---|---|---|
| `session_start` | Game (mod) started | `mod`, `mod_version`, `schema`, `session` (random id), `java` |
| `session_end` | Game closed normally | `dropped` (lines that could not be written, normally 0) |
| `world_leave` | Left the world | - |
| `hurt` | Took damage (including hits that only removed absorption) | `amount`, `health`, `max_health`, `absorption`, `cause`, `damage_type`, `attacker`, `attacker_name`, `direct`, `estimated` |
| `heal` | Health restored. Small heals (natural regeneration) within 1 s are merged into one line (`count` = merged heals); a single heal of 2 or more (potions and similar) is written at once | `amount` (total), `health` (latest), `max_health`, `count` |
| `death` | Died | `message` (death message text, contains names: only present with `include_names`), `message_key` (translation key, e.g. `death.attack.lava`), `cause`, `attacker`, `attacker_name`, `estimated`, `dimension`, `xp_level` |
| `respawn` | Respawned | `health`, `max_health`, `food`, `dimension` |
| `death_screen_open` / `death_screen_close` | Death screen opened / closed | `on` |
| `water_enter` / `water_leave` | Entered / left water | `on` |
| `submerged_start` / `submerged_end` | Head went under water / came out | `on` |
| `drowning_start` / `drowning_end` | Air reached 0 under water / recovered | `on` |
| `lava_enter` / `lava_leave` | Entered / left lava | `on` |
| `fire_start` / `fire_end` | Caught fire / fire went out | `on` |
| `freeze_start` / `freeze_end` | Started freezing in powder snow / fully thawed (1.17+) | `on` |
| `frozen_start` / `frozen_end` | Fully frozen (taking damage) / no longer (1.17+) | `on` |
| `effect_add` | Status effect applied | same fields as `effects[]` |
| `effect_change` | Amplifier changed or the remaining time was extended | same fields + `prev_amplifier`, `prev_duration` |
| `effect_remove` | Status effect ended | `effect`, `amplifier`, `level`, `category` |
| `level_up` / `level_down` | Experience level changed | `level`, `delta` |
| `dimension_change` | Changed dimension | `from`, `to` (e.g. `minecraft:the_nether`) |
| `gamemode_change` | Game mode changed | `from`, `to` (`survival`, `creative`, `adventure`, `spectator`) |
| `sleep_start` / `sleep_end` | Went to sleep / woke up | `on` |
| `elytra_start` / `elytra_end` | Started / stopped elytra flight | `on` |
| `mount` / `dismount` | Got on / off a vehicle or mount | `vehicle` (e.g. `minecraft:horse`) |
| `swim_start` / `swim_end` | Started / stopped the swimming pose (1.13+) | `on` |
| `sprint_start` / `sprint_end` | Started / stopped sprinting | `on` |
| `sneak_start` / `sneak_end` | Started / stopped sneaking | `on` |
| `move_start` / `move_end` | Started / stopped walking on the ground. Changes only after the horizontal speed stayed above / below 0.04 blocks per tick (sneak-walking included) for 0.25 s. Jumps keep the current value; more than 1.5 s without ground (falling, swimming, riding) ends it. Teleports do not count as walking | `on`, `pace` (`move_start` only: `walk` / `sprint`) |
| `jump` | Left the ground by jumping (jump key or auto-jump). Falling and knockback are not jumps. At most 4 per second | - |
| `swing` | Swung a hand. Swings while breaking blocks (including the short pause before the next block while attack is held) are covered by `mine_start` / `mine_end` instead. At most 4 per second | `hand` (`main` / `off`), `action` (`attack` / `use`, from the button that was pressed) |
| `mine_start` / `mine_end` | Block breaking progress started / stopped or finished (one pair per block). Blocks that break instantly (creative mode) only give a `swing` | `on` |

Water, head under water, swimming, sprinting and sneaking are written only after the new state held for 0.4 s, so flicker such as swimming at the surface is ignored. Danger signals (drowning, lava, fire, freezing) are written without delay.

When only absorption was lost, the damage information may arrive up to 3 ticks later and is still merged into `hurt`; absorption that runs out without damage is written as `absorption` after 3 ticks. Right after joining, respawning or changing dimension the client briefly holds default values, so comparing waits until the server has sent the real values (loading screen closed and values unchanged for 2 s, at most 10 s). This prevents fake heals or hits.

### Causes in `hurt` / `death`

| Field | Meaning |
|---|---|
| `cause` | One of the cause values below |
| `damage_type` | Damage type id reported by the game (e.g. `minecraft:mob_attack`). `null` before 1.19.4 or when estimated |
| `attacker` | Entity type id of the attacker (e.g. `minecraft:zombie`, `minecraft:player`) |
| `attacker_name` | Name of that entity (only with `include_names`, otherwise `null`) |
| `direct` | Entity type that actually hit (an arrow, for example) |
| `estimated` | `false` = cause reported by the game, `true` = estimated by the mod from the surroundings |

How causes are found

- 1.19.4 and newer (1.20.1, 1.21.x, 26.3): the damage information the server sends to the client (damage type and attacker) is used as is. `estimated: false`.
- Older versions (1.12.2, 1.16.5, 1.18.2): the client does not receive a cause, so it is estimated from the situation at the moment health dropped, in this order: below the world, fall distance right after landing, lava, no air under water, inside a block, cactus, sweet berry bush, frozen, fire block, magma block, nearby projectile / hostile mob / player, burning, wither or poison effect, empty food bar. `estimated: true`.
- `death` uses the translation key of the death message (`death.attack.lava` and so on), so it is mostly `estimated: false` even on old versions.

| `cause` | Meaning |
|---|---|
| `mob` | Hit by a mob |
| `player` | Hit by another player |
| `projectile` | Arrow, trident, fireball and similar |
| `fall` | Fall damage |
| `fire` | Fire (fire block, campfire, burning) |
| `lava` | Lava |
| `hot_floor` | Magma block |
| `drown` | Drowning |
| `explosion` | Explosion (creeper, TNT, firework, bed and similar) |
| `cactus` | Cactus |
| `sweet_berry` | Sweet berry bush |
| `suffocation` | Suffocating inside a block |
| `freeze` | Freezing in powder snow |
| `magic` | Magic (harmful potions and similar) |
| `poison` | Poison |
| `wither` | Wither |
| `void` | The void |
| `starve` | Starvation |
| `lightning` | Lightning |
| `falling_block` | Falling block (anvil and similar) |
| `fly_into_wall` | Flying into a wall with an elytra |
| `thorns` | Thorns enchantment |
| `world_border` | Outside the world border |
| `kill` | `/kill` |
| `generic` | Other generic damage |
| `unknown` | Unknown |

## Differences between versions

| Information | Missing in | What is written |
|---|---|---|
| Real damage type and attacker | 1.12.2, 1.16.5, 1.18.2 | Estimate, `estimated: true`, `damage_type: null` |
| Powder snow freezing | 1.12.2, 1.16.5 | `frozen_ticks` and `fully_frozen` are `null`, no freeze events |
| Swimming pose | 1.12.2 | `swimming` is `null`, no swim events |
| Sweet berry bush | 1.12.2 | not used as an estimated cause |
| Infinite effects | before 1.19.4 | `infinite` is always `false` |

Movement lines (`move_*`, `jump`, `swing`, `mine_*`, `sneak_*`) and `moving`, `sneaking`, `on_ground` work the same on every supported version.

## Configuration (`config/player-events.json`)

Created with default values on first start. If the file cannot be read, defaults are used and the file is left as it is.

```json
{
  "config_version": 2,
  "enabled": true,
  "path": "logs/player-events.jsonl",
  "max_file_mb": 10.0,
  "include_names": false,
  "heal_merge_ms": 1000.0,
  "heal_instant_min": 2.0,
  "toggle_debounce_ms": 400.0,
  "events": {
    "hurt": true,
    "jump": true
  }
}
```

| Key | Meaning |
|---|---|
| `config_version` | Format of this file (currently 2). Do not change it |
| `enabled` | `false` writes nothing |
| `path` | Log file path. Relative paths start at the game directory |
| `max_file_mb` | Rotate to `.1` above this size |
| `include_names` | `true` also writes your name (`player`), the attacker's name (`attacker_name`) and the death message text (`message`). With the default `false`, no value that can contain a name is written |
| `heal_merge_ms` | Time window for merging small heals (milliseconds). 0 writes every change |
| `heal_instant_min` | A single heal at least this big is written at once |
| `toggle_debounce_ms` | How long water / under water / swimming / sprinting / sneaking must hold before a line is written (milliseconds) |
| `events` | Turn single events on or off. Events not listed are on |

Files from versions before 1.3.0 (no `config_version`) are updated once on the first start of 1.3.0: sneak lines, which were off by default then, are turned on when both `sneak_start` and `sneak_end` are `false`, and keys that are no longer used are removed. Other values are kept. Set the sneak events to `false` again afterwards to turn them off.

## Building

Every folder is a separate Gradle project with its own Gradle Wrapper. Install the JDKs below and point `JAVA_HOME` at the right one for each folder.

| Folder | JDK that runs Gradle | Command |
|---|---|---|
| `core` | 17 or newer | `gradlew test` (unit tests) |
| `fabric-<ver>` (6 folders) | 25 | `gradlew build` -> `build/libs/pel-part-<ver>.jar` (the reader for that version) |
| `fabric` | 17 or newer | `gradlew build` -> `build/libs/player-event-log-<version>+fabric.jar` (build the six parts first) |
| `forge-1.20.1` | 17 | `gradlew build` |
| `forge-1.12.2` | 8 | `gradlew build` |
| `neoforge-1.21.1` | 21 | `gradlew build` |

To build everything at once, give the script the four JDK folders (or set `JAVA8_HOME`, `JAVA17_HOME`, `JAVA21_HOME` and `JAVA25_HOME`). The jars and `SHA256SUMS.txt` are collected in `dist/`.

```powershell
./build-all.ps1 -Jdk8 <path to JDK 8> -Jdk17 <path to JDK 17> -Jdk21 <path to JDK 21> -Jdk25 <path to JDK 25>
```

```sh
JAVA8_HOME=... JAVA17_HOME=... JAVA21_HOME=... JAVA25_HOME=... ./build-all.sh
```

Source layout

```
core/                 version independent part: format, JSON, state diffing, file writer, cause mapping, unit tests
shared/mojmap/        player reader with Mojang names (all Fabric versions and Forge 1.20.1)
shared/compat/<ver>/  the few calls whose names differ per Minecraft version
shared/fabric/        Fabric entry point (loads the running version's reader by name) and the tick hook (one mixin for all versions)
fabric-<ver>/         the reader for one Minecraft version, compiled with that version's mappings and Java, in package playereventlog.mc.v<ver>
fabric/               joins the common part (Java 8) and the six readers into the one Fabric jar
forge-1.20.1/         Forge 1.20.1 entry point and build settings
forge-1.12.2/         Forge 1.12.2 entry point, reader and build settings
neoforge-1.21.1/      NeoForge 1.21.1 entry point and build settings (same reader as Fabric 1.21.1)
```

## License

MIT. See `LICENSE`. Copyright (c) 2026 Stream Hearts.
