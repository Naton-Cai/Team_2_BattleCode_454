#!/bin/bash
# 43-map AGGRESSION self-play on Coeus. Survives SSH disconnect.
# Submit from the repo root:
#   mkdir -p matches
#   sbatch scripts/coeus-sweep-full.sbatch
# Does not write winners into Config.
#
#SBATCH --job-name=bc26-sweep
#SBATCH --time=10:00:00
#SBATCH --ntasks=1
#SBATCH --cpus-per-task=4
#SBATCH --mem=8G
#SBATCH --output=matches/coeus-%j.out
#SBATCH --error=matches/coeus-%j.err

set -euo pipefail

if [[ -n "${SLURM_SUBMIT_DIR:-}" ]]; then
  cd "$SLURM_SUBMIT_DIR"
else
  cd "$(dirname "$0")/.."
fi

mkdir -p matches

if command -v module >/dev/null 2>&1; then
  module purge 2>/dev/null || true
  module load jdk/21 2>/dev/null || module load java/21 2>/dev/null || module load Java/21 2>/dev/null || true
fi

if ! command -v java >/dev/null 2>&1; then
  echo "java not on PATH. Load a JDK 21 module or set JAVA_HOME." >&2
  exit 1
fi

java -version

./gradlew sweepAggression -PcompareMaps=full --console=plain
