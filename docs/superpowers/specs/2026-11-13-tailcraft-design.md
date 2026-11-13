# Tailcraft design

Date: 2026-11-13
Repo: `RefinedChicken/tailcraft` (fork of `tailscale/tailcat-for-minecraft`)

## Intent

Tailcraft lets people play Minecraft together without port forwarding or
installing Tailscale. It started as a prototype that only shared singleplayer
worlds. That prototype was a client-only Fabric mod that ran a Tailcat helper
process and swapped one-time `mcl1_...` invites.

Tailcraft extends that prototype into a single jar that works in three ways:

- **Dedicated server:** put the jar in `mods/` and forget about it. On first
  start the server gets a permanent Tailcat address and prints it to the
  console. The admin shares that address the same way they would share an IP.
- **Client:** players install the same jar and nothing in the UI looks
  different. They paste the server's address into the normal Add Server or
  Direct Connect box, and it connects and pings like any other server.
- **Singleplayer:** "Open to LAN" gets a Tailcraft option. Turning it on
  creates a new temporary address every time the world is opened, the same
  way vanilla picks a new random port.

The goal is a mod that other people can install and understand without
reading docs. The address works like an IP that anyone can paste in.

## Goals

1. One Fabric jar (`environment: "*"`) with a client entrypoint and a
   dedicated-server entrypoint.
2. Dedicated server:
   - Tailcraft is on by default.
   - On first start, create and save a persistent Tailcat identity (private
     key, pre-shared key, pinned DERP region). Reuse it on every start after
     that, so the address never changes until an admin rotates it.
   - Print the address to the console on every start. Also write it to
     `config/tailcraft.json` so it's easy to copy.
   - The normal TCP port keeps working exactly as before, for LAN players
     and for troubleshooting.
3. Client:
   - A Tailcraft address typed or pasted into Add Server or Direct Connect
     connects through Tailcat.
   - Saved servers with Tailcraft addresses show MOTD, player count and ping
     in the server list, like any other server.
   - No new buttons on the main multiplayer screen.
4. Singleplayer:
   - Remove the "Share with Tailcat" button from the pause menu.
   - Add a Tailcraft on/off control to the Open to LAN screen. It's off by
     default.
   - When it's on, starting the LAN world also starts an ephemeral Tailcat
     host. The address goes into chat as a click-to-copy message, next to
     vanilla's "Local game hosted on port X" message.
   - The address dies when the world closes. A new one is made next time.
5. Rebrand the project to Tailcraft:
   - mod id `tailcraft`
   - display name "Tailcraft"
   - Java package and Maven group moved away from `com.tailscale`
     (see "Calls made by Claude")
   - `authors` set to the fork owner
   - README rewritten for the new behavior
   - upstream's BSD-3-Clause license and copyright line kept

## Non-goals (for now)

- **WASM helper.** It's parked until a later phase. Keep the native helper
  process. The motivation for revisiting it: some shared Minecraft hosts
  block running extracted executables, and WASM would avoid that.
- Paper, Spigot, Forge and NeoForge. This is Fabric only.
- Supporting more than one Minecraft version.
- Access control on top of Minecraft's own. Online-mode auth, whitelist, bans
  and ops do that job. A Tailcraft address is as public as an IP.
- Joining Tailscale tailnets or using Tailscale accounts.
- Compatibility with the prototype's `mcl1_` invite format.

## Anti-goals

These would make the result a failure even if it technically works:

- Changing vanilla UI for players who never use Tailcraft. That includes
  extra buttons on the multiplayer screen and layout shifts.
- Breaking or replacing the server's normal port.
- A server address that changes on restart without the admin asking for it.
- A singleplayer address that survives after the world closes.
- Writing the persistent server private key anywhere other than its key
  file. That includes logs and `tailcraft.json`.

## Constraints and known facts

- **Upgrade Tailcat** from the pinned 2026-08-22 version to current upstream
  (`e00cdde6ff78`, 2026-11-05 or later). Current upstream has what this
  design needs:
  - `tailcat.PrivateKey` is a saveable identity: node private key, plus a
    `ConnInfo` with the server public key, the disco public key (derived from
    the node key), the pre-shared key and the region.
  - `ConnInfo.RegionID = -1` auto-picks the best region at startup.
    `Expand` with a positive `RegionID` pins that region.
  - `tailcat.Addr` is the compact `tc...` address (base64url CBOR).
    `ConnBlob` is deprecated.
  - `Server.Key`, `Server.PresharedKey` and `Server.RegionID` accept a loaded
    identity.
- **The address is a secret, in the same sense an IP is semi-public.** It
  contains the WireGuard pre-shared key, so anyone who has it can reach the
  server's virtual port 25565. Nothing else is reachable. Minecraft auth
  still applies after that.
- **The address is longer than vanilla's 128-character limit** on the
  address field: about 140 characters with three 32-byte keys. The address
  fields in Add Server and Direct Connect must accept it.
  `ServerAddress.parse` / IDN validation would also reject it as a hostname,
  so the address must be recognized before vanilla parses it.
- **The native helper stays one Go binary**, cross-compiled for windows,
  darwin and linux on amd64 and arm64, and extracted and checksum-verified
  by `NativeHelper`. Its stdout still carries the JSON event protocol.
- **Minecraft 1.21.1, Fabric Loader 0.16.10+, Java 21**, as in the existing
  project. This may change; see open questions.
- **Dev offline-auth bypass:** `MCLINK_DEV_OFFLINE_AUTH`, renamed to
  `TAILCRAFT_DEV_OFFLINE_AUTH`. It stays restricted to the Fabric
  development environment.

## Behavior details

### Dedicated server

- **Files:**
  - `config/tailcraft/server.key`: the persistent `tailcat.PrivateKey`, with
    the region pinned to a concrete `RegionID` after the first `Expand`. On
    POSIX it's created with `0600` permissions; on Windows, best-effort
    owner-only.
  - `config/tailcraft.json`: `enabled` (default `true`) and `address`
    (informational, rewritten on every start from the key; never read back
    as truth).
- **Startup:** on server started, spawn the helper in host mode. Point it at
  the server's own listen port, pass the key-file path, and wait for `ready`.
  Then log a clear banner with the address.
- **Port:** the target is `127.0.0.1:<server-port>` when `server-ip` is empty
  or loopback. Otherwise it's `server-ip:<server-port>`, since the admin
  bound the server there.
- **Commands** (op level 4 or console):
  - `/tailcraft address`: print the address again.
  - `/tailcraft regenerate`: delete the key, restart the helper with a new
    identity, print the new address, and warn that the old address no longer
    works.
- **Failure:** the helper can fail, for example on a noexec temp dir, a
  blocked DERP connection, or an unsupported platform. The server must keep
  running normally on its regular port. Log one clear error with the reason
  and a hint.
- The helper exits on server stop.

### Client

- **What counts as an address:** a string that starts with `tc`, has no `.`
  or `:`, and decodes as a valid `tailcat.Addr`. Anything else goes through
  the vanilla path unchanged. Decoding uses the same rules as the Go side;
  see "Left to the builder".
- **Connecting:** when connecting to, or pinging, a Tailcraft address, get or
  start the join tunnel for that address. Redirect Minecraft to the tunnel's
  `127.0.0.1:<port>`. The server list entry still shows and stores the `tc...`
  address, not the loopback port.
- **Tunnels:**
  - At most one tunnel per distinct address. Pings and the real connection
    share it.
  - A tunnel closes after it has had no open connections for a short idle
    period, and on client shutdown.
- **Errors:** a failed tunnel shows up as a normal vanilla connection or ping
  failure with a readable reason, not a crash.

### Singleplayer

- **Open to LAN screen:** add a "Tailcraft: Off/On" control in vanilla style,
  using the existing layout patterns.
- **On "Start LAN World" with Tailcraft on:**
  1. Start the ephemeral host targeting the LAN port.
  2. Post a chat message with the address as a click-to-copy component.
  3. Show a toast if starting fails.
- The host stops when the integrated server stops.
- The prototype's online-mode handling and dev bypass behave as they do
  today.

### Helper CLI (Go)

- `host --target <addr:port> [--key-file <path>]`:
  - With `--key-file`: load the identity, or create it and save it with
    `RegionID` pinned on first run.
  - Without it: ephemeral identity, region auto-picked.
  - Emits `{"type":"ready","mode":"host","address":"tc..."}`.
- `join --address <tc...>`:
  - Emits `{"type":"ready","mode":"join","address":"127.0.0.1:<port>"}`
    (the local listen address).
- The prototype's `invite` package and the `mcl1_` format are removed.
- Kept:
  - virtual port 25565 only
  - the cap on concurrent streams (64)
  - loopback-only join listener
  - stdin-close shutdown
- The host-mode target check is relaxed from "loopback only" to "loopback or
  the explicit server-ip the Java side passes". The Java side never passes
  user-typed targets.

## Calls made by Claude (check these)

- Java package `io.github.refinedchicken.tailcraft` and Maven group
  `io.github.refinedchicken`. This follows the Modrinth/Fabric convention
  for a GitHub-hosted mod and avoids implying Tailscale ownership.
- Use the raw Tailcat `tc...` address rather than a Tailcraft-specific
  prefix. It's shorter and works with other Tailcat tools. Detection is by
  successful decode.
- Show the singleplayer address in chat (click to copy) rather than on a
  separate screen. This matches how vanilla reports the LAN port. Vanilla
  greys out "Open to LAN" once a world is published, so chat is also where
  the host finds the address again.
- One helper process per address on the client, instead of one long-lived
  helper that multiplexes. It's simpler and reuses the existing process
  protocol.
- The server key lives in its own file, separate from `tailcraft.json`, so
  people can paste the config into support chats without leaking the key.
- Include `/tailcraft regenerate`. It's the only remedy if a server's address
  leaks to someone the admin wants out, beyond whitelisting.

## Left to the builder

- Mixin targets and injection points for:
  - address-field max length
  - connect and ping redirection
  - the Open to LAN screen
- How the Java side decodes or validates `tc...` addresses. Options: a small
  CBOR/base64 check in Java, or asking the helper. Either way, false
  positives must be impossible for normal hostnames.
- Idle timeout value for client tunnels.
- Updating the Tailscale Go toolchain revision in `scripts/build-natives.sh`
  if the Tailcat upgrade needs it.
- Test layout:
  - Go unit tests for identity save/load and region pinning
  - Java unit tests for address detection
  - a manual end-to-end check: a dedicated server via `runServer`, plus a
    client, over DERP and direct paths

## Open questions for the owner

1. Minecraft version: TBD
2. Release distribution: TBD

## Build-time calls (made during implementation)

- Tailcat was upgraded to `e00cdde6ff78`. It builds with stock Go, so the
  patched Tailscale toolchain was dropped: `scripts/build-natives.sh` pins Go
  1.27.4 and verifies the official download against go.dev's published
  SHA-256.
- Helper binaries are no longer committed (they were about 115 MB per
  rebuild). They are gitignored and built by the script locally and by CI.
- The key file is the tailcat CLI's JSON encoding of `tailcat.PrivateKey`, with
  `RegionID` pinned. The address uses the short form (region by ID), which
  comes to about 154 characters.
- The Java side recognizes an address when it starts with `tc`, is at least 80
  base64url characters, and decodes to a CBOR map header. The helper does the
  full parse.
- Client tunnels close after 60 seconds unused. A tunnel that carries the
  current game connection never counts as idle.
- The server helper retries with backoff from 5 seconds up to 5 minutes. It
  does not retry when the helper can't start at all (missing platform binary
  or an exec-blocking host).
- `/tailcraft regenerate` asks for `confirm` before replacing the address.
- Vanilla `/publish` never starts Tailcraft. Only the Open to LAN toggle does.
