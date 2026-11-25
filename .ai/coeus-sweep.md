# Coeus 43-map aggression sweep

How to log into PSU Coeus, forward the school GitHub SSH key, submit the 21-pairing array, and check that it is actually playing games. This job does not write winners into `Config`.

Two different SSH keys:

| Key | File | Used for |
| --- | --- | --- |
| Cluster | `~/.ssh/id_ed25519_psu` | Logging into `login1.coeus.rc.pdx.edu` as `jorgep` |
| GitHub (school) | `~/.ssh/id_ed25519_edu` | `git clone` / `git pull` from Coeus, via agent forwarding |

Do not copy either private key onto the cluster. Do not use the personal GitHub key (`~/.ssh/id_ed25519`).

## 1. Log in (laptop)

Connect the PSU VPN first. Coeus is not reachable without it.

```bash
ssh-add ~/.ssh/id_ed25519_edu
```

Puts the school GitHub key in your local ssh-agent so `-A` can offer it on Coeus. You may be prompted for the key passphrase. This does not log you into the cluster.

```bash
ssh -A -i ~/.ssh/id_ed25519_psu jorgep@login1.coeus.rc.pdx.edu
```

Opens a shell on the Coeus login node.

- `-i ~/.ssh/id_ed25519_psu` authenticates you to Coeus with the cluster key.
- `-A` forwards the agent, so `git` on Coeus can use `id_ed25519_edu` without that file existing on the cluster.

Stay on the login node for `git` / `sbatch` / `squeue`. Do not run the sweep here, and do not use `tmux` or `nohup` for this job.

## 2. Get the repo on Coeus (first time)

Still on Coeus:

```bash
git clone git@github.com:Naton-Cai/Team_2_BattleCode_454.git
```

Creates `~/Team_2_BattleCode_454` over SSH. This is the step that needs the forwarded GitHub key. If it prints `Permission denied (publickey)`, you skipped `ssh-add` or `-A`.

```bash
cd Team_2_BattleCode_454
git checkout feature/self-play-aggression
```

`cd` enters the clone. `git checkout` switches to the branch that has `scripts/coeus-sweep-full.sbatch`.

## 3. Run it again (already cloned)

On the laptop, VPN on, then the same login as §1. On Coeus:

```bash
cd ~/Team_2_BattleCode_454
```

Returns to the clone. Slurm output paths are relative to this directory.

```bash
git pull
```

Fast-forwards to whatever is on `origin/feature/self-play-aggression`. Needed after you commit/push sbatch or sweep-script fixes from the laptop. If this fails with `Permission denied`, log out and come back with `ssh-add` + `ssh -A`.

```bash
scripts/coeus-submit-sweep.sh
```

Cancels your Slurm jobs, deletes and recreates `matches/pairs`, submits the 21-task array (`#SBATCH --array=0-20`), and queues merge with `afterok` on that array id. Prints `ARRAY_ID=` (example: `6558251`) then `squeue`. Each array task loads Java 21 (`openjdk/21.0.0_35-none-none`), rsyncs the tree into `work/<task>`, isolates `GRADLE_USER_HOME`, disables the Gradle daemon and file watching, and runs one `PAIR_INDEX` on the full 43-map pool. The grid is `{-1, 0, 0.25, 0.5, 0.75, 1}`; `-1` is the live DEFAULT mix. Merge writes `matches/pairs/summary.txt`. If any array task fails, merge is not satisfied (`DependencyNeverSatisfied`) and you run this script again after the array is fixed.

Do not run this if you intend to leave a live sweep alone; it `scancel`s every job under your account.

You can disconnect. The jobs keep running on compute nodes.

## 4. Check that it is running

From the repo on Coeus (`~/Team_2_BattleCode_454`):

```bash
squeue -u $USER
```

Lists **your** Slurm jobs only.

- `bc26-swe` / `R` — that pairing task is running on a compute node.
- `bc26-mer` / `PD` `(Dependency)` — merge is waiting for the array. Expected until all 21 finish.
- Empty — Slurm has nothing left for you (success, failure, or cancelled). Then look at logs; an empty queue is not a win by itself.

Replace `6558251` below with the id `echo "$ARRAY_ID"` printed.

```bash
tail -20 matches/coeus-6558251_0.out
```

Stdout for array task 0. A healthy run shows Gradle tasks, then lines like:

```text
[server] -------------------- Match Starting --------------------
[server] ratplayer_a0 vs. ratplayer_a1 on arrows
```

`FAILURE: Build failed` here means Gradle died (wrong Java, NFS file watching, etc.). `head` of the matching `.err` is the next place to look.

```bash
ls matches/pairs
```

Pair logs (`a=-1-vs--1.log`, `a=0-vs-0.log`, …) appear as tasks finish, not at submit time. All 21 files plus `summary.txt` means merge ran.

```bash
cat matches/pairs/summary.txt
```

Win matrix after merge. Copy the pair logs home from the laptop (VPN on):

```bash
scripts/coeus-fetch-pairs.sh
```

Uses the cluster key `~/.ssh/id_ed25519_psu` and rsyncs `~/Team_2_BattleCode_454/matches/pairs/` (logs, `summary.txt`, and pair-prefixed `.bc26` files the array copies after each task) plus leftover `work/**/*.bc26` into a timestamped `matches-coeus-pairs/` folder, then prints `summary.txt`. Pass a destination to reuse a path: `scripts/coeus-fetch-pairs.sh matches-coeus-pairs/latest`. Fetch the current run before the next `sbatch`; that job `rm -rf`s each `work/<task>` on start.

## 5. Warning: WebSocket `BindException` is noise

You will likely see this in `matches/coeus-<jobid>_<task>.err`:

```text
Error from [unopened WebSocket]: java.net.BindException: Address already in use
```

That is **not** a failed match. Do not cancel the array for it.

The engine still tries to bind a visualizer WebSocket even though the sweep runs headless (`-Dbc.server.mode=headless`, `-Dbc.server.wait-for-client=false` in `scripts/compare-players.sh`). Several array tasks often land on the same node (for example ten on `compute034`) and they all want that same default port. The second process loses the bind, logs the error on stderr, and continues the game.

How to tell noise from a real crash:

| `.err` / `.out` | Meaning |
| --- | --- |
| `Error from [unopened WebSocket]: java.net.BindException: Address already in use` | Ignore. Check `.out` for `Match Starting`. |
| `FAILURE: Build failed` plus `FileWatchingFilter` / `BuildLifecycleAwareVirtualFileSystem` | Gradle died on NFS. Isolate `GRADLE_USER_HOME`, `org.gradle.daemon=false`, `org.gradle.vfs.watch=false` (already in the sbatch). |
| `Killed`, exit `137`, or `oom_kill event` | Slurm killed Java for exceeding `--mem`. The sbatch requests 16G and caps the heap with `-Xmx12g`. |
| `Unsupported class file major version` or a Java 8 `java -version` | Wrong JDK. The sbatch must `module load openjdk/21.0.0_35-none-none`. |

`head` of `.err` is a bad health check by itself: Java prints its version there, then the WebSocket line, even while games are winning on `.out`.
