#!/bin/bash
# Merge array pair logs into the win matrix. Submit after the array:
#   sbatch --dependency=afterok:<array_jobid> scripts/coeus-sweep-merge.sbatch
#
#SBATCH --job-name=bc26-merge
#SBATCH --time=00:10:00
#SBATCH --ntasks=1
#SBATCH --cpus-per-task=1
#SBATCH --mem=1G
#SBATCH --output=matches/coeus-merge-%j.out
#SBATCH --error=matches/coeus-merge-%j.err

set -euo pipefail

if [[ -n "${SLURM_SUBMIT_DIR:-}" ]]; then
  cd "$SLURM_SUBMIT_DIR"
else
  cd "$(dirname "$0")/.."
fi

python3 scripts/summarize-aggression-sweep.py \
  matches/pairs ratplayer1 ratplayer2 matches/pairs/summary.txt
