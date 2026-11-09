# Block Shuffle (Fabric mod, Minecraft 1.21.4)

Every round, each player gets a random block and has 5 minutes to stand on it.
Only the player who finishes is told their time. Whoever fails loses that round.
Rounds repeat until you stop the game.

## Commands
- `/blockstart` - starts the challenge
- `/blockstop` - stops it and shows final scores

(Minecraft already has a `/stop` command that closes the whole server, so the mod uses `/blockstart` and `/blockstop`.)
Anyone can use these commands, and you do not need cheats turned on.

## Block difficulty
- Rounds 1-3: easy blocks (dirt, stone, planks, sand, furnace...)
- Rounds 4-6: medium blocks added (iron ore, bricks, TNT, ice, bookshelf...)
- Round 7+: harder blocks added (gold block, obsidian, diamond ore, slime block...)

Nothing from the Nether or End, and no netherite blocks.

## Get the mod file (.jar) with GitHub - no Java needed

1. Go to https://github.com and create a free account.
2. Click the "+" at the top right, then "New repository". Name it anything, for example `block-shuffle`. Click "Create repository".
3. Click "uploading an existing file".
4. Unzip this project on your computer. Open the unzipped `BlockShuffleMod` folder, select EVERYTHING inside it
   (including the `.github` folder, `src` folder and all the files), and drag it into the GitHub page.
   Do not upload the .zip file itself - GitHub will not unzip it.
5. Click "Commit changes".
6. Open the "Actions" tab. A build called "Build mod" starts by itself. Wait a few minutes until it has a green tick.
7. Click the finished build, scroll to "Artifacts" at the bottom, and download `BlockShuffle-mod`.
8. Unzip that download. Inside is `blockshuffle-1.0.0.jar` - this is your mod.

If the `.github` folder did not upload, click "Add file" > "Create new file", type
`.github/workflows/build.yml` as the name, paste the contents of that file from this project, and commit.

If the build shows a red cross, open it, copy the error text, and send it to Claude.

## Install it
1. Install the Fabric Loader for Minecraft 1.21.4 from https://fabricmc.net/use/installer/
2. Download Fabric API (version for 1.21.4) from https://modrinth.com/mod/fabric-api and put it in your `mods` folder.
3. Put `blockshuffle-1.0.0.jar` in the same `mods` folder
   (Windows: press Win+R, type `%appdata%\.minecraft\mods`).
4. Start Minecraft with the Fabric profile.

## Playing with friends
The mod only runs on the host. A friend does not need to install it, but they must play 1.21.4.
- LAN: open your world to LAN (Esc > Open to LAN). Friends on the same Wi-Fi join you.
- Dedicated Fabric server: put both the Fabric API and this mod in the server's `mods` folder.
- You can also just send the jar to a friend so they can host.

## Settings
At the top of `BlockShuffleMod.java`: `ROUND_SECONDS`, `BREAK_SECONDS`, `ROUNDS_PER_TIER`, `TELL_EVERYONE_ON_FINISH`.
Players who join in the middle of a round start playing from the next round.
