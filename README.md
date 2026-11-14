# Tailcraft

Play Minecraft with friends without port forwarding and without installing
Tailscale. Tailcraft is a Fabric mod for Minecraft 1.21.1 to 1.21.11 that connects
players to a server through [Tailcat](https://github.com/tailscale/tailcat):
userspace WireGuard, direct NAT traversal when possible, and Tailscale's DERP
relays when not.

The same jar goes on the server and on every player's client. Each release
has one jar per Minecraft range; pick the one whose name includes your
version:

| Minecraft | Jar |
| --- | --- |
| 1.21.1 to 1.21.4 | `tailcraft-<version>+mc1.21.1-1.21.4.jar` |
| 1.21.5 to 1.21.10 | `tailcraft-<version>+mc1.21.5-1.21.10.jar` |
| 1.21.11 | `tailcraft-<version>+mc1.21.11.jar` |

> [!NOTE]
> Tailcraft is a community fork of Tailscale's
> [Tailcat for Minecraft](https://github.com/tailscale/tailcat-for-minecraft)
> prototype. It is not made, supported or endorsed by Tailscale.

## Running a server

1. Put the Tailcraft jar and [Fabric API](https://modrinth.com/mod/fabric-api)
   in the server's `mods/` folder.
2. Start the server. When it's ready, the console prints a Tailcraft address
   (a long string starting with `tc`). It is also saved in
   `config/tailcraft.json`.
3. Send that address to your players.

The address stays the same across restarts. The server's normal port keeps
working as before, for LAN players and troubleshooting.

Things to know:

- **The address is like a public IP.** Anyone who has it can reach your
  server. Minecraft's own protections still apply: online-mode login,
  whitelist, bans and ops.
- **Tailcraft players show up as `127.0.0.1`** in the server log, because the
  helper connects to the server locally. IP bans don't work for them; ban by
  name instead.
- **`config/tailcraft/server.key` is the server's identity.** Keep it private.
  Anyone who copies it can impersonate your server. Back it up if you want to
  keep the address when you move the server.
- To turn Tailcraft off, set `"enabled": false` in `config/tailcraft.json`.

Commands (op level 4 or the console). `/tc` is a short alias, so
`/tc address` and `/tc regenerate confirm` work too:

| Command | What it does |
| --- | --- |
| `/tailcraft address` | Shows the address again (click to copy in game). |
| `/tailcraft regenerate confirm` | Replaces the address. The old one stops working and players have to update their saved server. |

If the network helper loses its connection, Tailcraft logs a warning and
retries automatically. The server keeps running either way.

## Joining a server

1. Install Tailcraft and Fabric API.
2. In **Multiplayer**, use **Add Server** or **Direct Connection** and paste
   the `tc...` address into the normal *Server Address* box.

That's it. The server shows its MOTD and player count, and you join like any
other server.

## Sharing a singleplayer world

1. Pause, then click **Open to LAN**.
2. Switch **Tailcraft** to **ON**, then click **Start LAN World**.
3. Click **[Copy]** in the chat message and send the address to your friends.

Each time a world is opened this way, it gets a new temporary address. The
address stops working when you close the world.

## How it works

Each jar contains a small Go helper (`helper/`), built for Windows, macOS and
Linux on x86-64 and ARM64. The mod extracts the helper for the current platform
into `.tailcraft/` in the game directory, verifies its SHA-256, and runs it as
a child process:

- **Hosting:** the helper runs a Tailcat server that accepts only virtual TCP
  port 25565 and forwards it to the Minecraft server's local port.
- **Joining:** when you connect to (or ping) a `tc...` address, the mod starts
  a helper that listens on `127.0.0.1:<random port>`. Minecraft connects there,
  and the helper tunnels the connection to the server.

The address contains the server's public keys, a WireGuard pre-shared key and
a relay region ID. The dedicated server pins its region on first start so the
address never changes.

## Building

Requirements: JDK 25 (Loom needs it; the mod itself still runs on Java 21),
`curl`, `tar` and `jq`. The script downloads and
verifies the pinned Go toolchain if a matching `go` isn't already on `PATH`.

```sh
scripts/build-natives.sh   # builds the helpers into mod/src/main/resources/assets/tailcraft/native/
./gradlew :mod:build       # jar is written to mod/build/libs/
```

Each folder in `mod/versions/` is one Minecraft range: `version.properties`
pins its Minecraft, Yarn and Fabric API versions, and `java/` holds the few
classes whose code differs between ranges (`VersionCompat`). The build uses
`minecraft_target` from `gradle.properties` (1.21.1 by default); pass another
folder name to build a different jar:

```sh
./gradlew :mod:build -Pminecraft_target=1.21.11
```

Helper tests: `cd helper && CGO_ENABLED=0 go test ./...`

### Development

- `./gradlew :mod:runServer --args=nogui` runs a dev server in `mod/run-server/`
  (accept the EULA in `mod/run-server/eula.txt` first).
- `./gradlew :mod:runClient` runs a dev client. To join a dev server straight
  away with an offline account, set `online-mode=false` on the dev server and
  pass `--args="--username Dev --quickPlayMultiplayer tc..."`.
- To test singleplayer sharing with two offline clients, launch the host with
  `TAILCRAFT_DEV_OFFLINE_AUTH=1`. This only works in Fabric's development
  environment, and the world's original online-mode setting is restored when
  sharing stops.
- `TAILCRAFT_HELPER=/path/to/tailcraft-helper` uses a locally built helper
  (development environment only).
