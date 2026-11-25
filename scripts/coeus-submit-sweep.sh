#!/usr/bin/env bash
# Reset matches/pairs and submit the 43-map array + merge. Run on Coeus from
# the repo (cd ~/Team_2_BattleCode_454). Cancels your existing Slurm jobs first
# so a second array does not stack. Does not write Config. Does not git pull.
set -euo pipefail
cd "$(dirname "$0")/.."

if ! command -v sbatch >/dev/null 2>&1; then
  echo "sbatch not found. Run this on Coeus, not the laptop." >&2
  exit 1
fi
if [[ ! -f scripts/coeus-sweep-full.sbatch || ! -f scripts/coeus-sweep-merge.sbatch ]]; then
  echo "Missing Coeus sbatch files. Run from the Team_2_BattleCode_454 clone." >&2
  exit 1
fi

scancel -u "$USER" || true
rm -rf matches/pairs
mkdir -p matches/pairs
ARRAY_ID=$(sbatch --parsable scripts/coeus-sweep-full.sbatch)
echo "ARRAY_ID=$ARRAY_ID"
sbatch --dependency=afterok:${ARRAY_ID} scripts/coeus-sweep-merge.sbatch
squeue -u "$USER"
