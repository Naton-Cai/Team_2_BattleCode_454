#!/bin/bash
# 43-map AGGRESSION self-play on Coeus: one unique pair per array task.
# Isolated work/ copies so 15 Gradle runs do not share build/.
# Submit from the repo root:
#   mkdir -p matches/pairs
#   ARRAY_ID=$(sbatch --parsable scripts/coeus-sweep-full.sbatch)
#   sbatch --dependency=afterok:${ARRAY_ID} scripts/coeus-sweep-merge.sbatch
# Does not write winners into Config.
#
#SBATCH --job-name=bc26-sweep
#SBATCH --array=0-14
#SBATCH --time=01:30:00
#SBATCH --ntasks=1
#SBATCH --cpus-per-task=2
#SBATCH --mem=8G
#SBATCH --output=matches/coeus-%A_%a.out
#SBATCH --error=matches/coeus-%A_%a.err

set -euo pipefail

if [[ -n "${SLURM_SUBMIT_DIR:-}" ]]; then
  SUBMIT="$SLURM_SUBMIT_DIR"
else
  SUBMIT="$(cd "$(dirname "$0")/.." && pwd)"
fi
cd "$SUBMIT"

if [[ -z "${SLURM_ARRAY_TASK_ID:-}" ]]; then
  echo "SLURM_ARRAY_TASK_ID is unset. Submit with sbatch, not bash." >&2
  exit 1
fi

mkdir -p matches/pairs work

if command -v module >/dev/null 2>&1; then
  module purge 2>/dev/null || true
  module load jdk/21 2>/dev/null || module load java/21 2>/dev/null || module load Java/21 2>/dev/null || true
fi

if ! command -v java >/dev/null 2>&1; then
  echo "java not on PATH. Load a JDK 21 module or set JAVA_HOME." >&2
  exit 1
fi

java -version

JOBDIR="$SUBMIT/work/$SLURM_ARRAY_TASK_ID"
rm -rf "$JOBDIR"
mkdir -p "$JOBDIR"
rsync -a \
  --exclude '/build/' \
  --exclude '/matches/' \
  --exclude '/work/' \
  --exclude '/.gradle/' \
  --exclude '/client/' \
  --exclude '/.git/' \
  "$SUBMIT"/ "$JOBDIR"/

cd "$JOBDIR"
export PAIR_INDEX="$SLURM_ARRAY_TASK_ID"
bash scripts/sweep-aggression.sh --full-pool

cp matches/sweep-*/a=*-vs-*.log "$SUBMIT/matches/pairs/"
