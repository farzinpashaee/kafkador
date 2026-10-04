#!/usr/bin/env bash
#
# Send a JSON message to a Kafka topic, once or every X seconds.
#
# The message is read from event.json next to this script (change it with -f). On start the script asks how
# often to send it; answer 0 (or press Enter) to send once, or a number of seconds to keep sending until Ctrl+C.
# Placeholders in the JSON (<datetime>, <random-int>, <random-double>, <random-string>) get fresh values for
# every message; see fill_placeholders below.
#
# Uses the first producer it finds: kcat (kafkacat), kafka-console-producer(.sh) on the PATH or under
# $KAFKA_HOME/bin, or - as a fallback - the apache/kafka Docker image. Nothing else needs to be installed.
#
# Examples:
#   ./send-json.sh -t orders                         # sends event.json, asks for the interval
#   ./send-json.sh -t orders -i 5                    # every 5 seconds, no question
#   ./send-json.sh -t orders -i 3 -r 5               # every 3 + random 0..5 seconds
#   ./send-json.sh -t orders -f other.json -k ord-1  # another file, with a message key
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

BROKER="${KAFKA_BOOTSTRAP:-localhost:9092}"
TOPIC=""
KEY=""
FILE="$SCRIPT_DIR/event.json"
INTERVAL=""
RANDOM_DELAY=0
TOOL="auto"
DRY_RUN=false
KAFKA_IMAGE="${KAFKA_IMAGE:-apache/kafka:latest}"

usage() {
  cat <<EOF
Usage: $(basename "$0") -t TOPIC [options]

Options:
  -t TOPIC     Topic to send to (required)
  -f FILE      JSON file to send (default: event.json next to this script)
  -b BROKER    Bootstrap server(s), host:port[,host:port] (default: \$KAFKA_BOOTSTRAP or localhost:9092)
  -k KEY       Message key (optional)
  -i SECONDS   Send every SECONDS until Ctrl+C; 0 sends once. Skips the start-up question.
  -r SECONDS   Random extra delay: each wait is the interval plus a random 0..SECONDS (repeat mode only)
  -T TOOL      Force a producer: kcat | console | docker (default: auto-detect)
  -n           Dry run: validate and print what would be sent, then exit
  -h           Show this help

Each JSON document in the file becomes one Kafka message (multi-line JSON is compacted to one line).

Placeholders, filled with fresh values for every message (quote them to get a string):
  <datetime>                                     current UTC time, ISO-8601
  <random-int>    or <random-int:MIN:MAX>        integer, default 1..1000
  <random-double> or <random-double:MIN:MAX>     decimal with 2 places, default 0..1000
  <random-string> or <random-string:LENGTH>      letters and digits, default length 8
EOF
}

die() { echo "Error: $*" >&2; exit 1; }

while getopts ":t:f:b:k:i:r:T:nh" opt; do
  case "$opt" in
    t) TOPIC="$OPTARG" ;;
    f) FILE="$OPTARG" ;;
    b) BROKER="$OPTARG" ;;
    k) KEY="$OPTARG" ;;
    i) INTERVAL="$OPTARG" ;;
    r) RANDOM_DELAY="$OPTARG" ;;
    T) TOOL="$OPTARG" ;;
    n) DRY_RUN=true ;;
    h) usage; exit 0 ;;
    :) die "option -$OPTARG needs a value (see -h)" ;;
    *) die "unknown option -$OPTARG (see -h)" ;;
  esac
done

[ -n "$TOPIC" ] || { usage >&2; die "a topic is required (-t TOPIC)"; }
# A tab separates key and value for the console producer, so the key itself can't contain one.
case "$KEY" in *$'\t'*) die "the key must not contain a tab character" ;; esac

is_interval() { [[ "$1" =~ ^[0-9]+([.][0-9]+)?$ ]]; }

# ---- Template: placeholders, validation and compaction ---------------------------------------------------
[ -r "$FILE" ] || die "cannot read JSON file: $FILE"
TEMPLATE="$(cat "$FILE")"
[ -n "${TEMPLATE//[[:space:]]/}" ] || die "the JSON file is empty: $FILE"

# Replaces every placeholder with a fresh value; each occurrence gets its own. The file's quotes decide the
# JSON type: "<random-int>" stays a string, <random-int> becomes a number.
fill_placeholders() {
  awk -v seed="$RANDOM$RANDOM" -v now="$(date -u +%Y-%m-%dT%H:%M:%SZ)" '
    BEGIN { srand(seed); chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789" }
    function rint(lo, hi) { return lo + int(rand() * (hi - lo + 1)) }
    function rstr(len,   s, i) { s = ""; for (i = 0; i < len; i++) s = s substr(chars, int(rand() * 62) + 1, 1); return s }
    {
      line = $0; out = ""
      while (match(line, /<(datetime|random-int|random-double|random-string)(:[-0-9.]+)*>/)) {
        n = split(substr(line, RSTART + 1, RLENGTH - 2), a, ":")
        if (a[1] == "datetime") {
          v = now
        } else if (a[1] == "random-int") {
          v = (n >= 3) ? rint(a[2] + 0, a[3] + 0) : rint(1, 1000)
        } else if (a[1] == "random-double") {
          lo = (n >= 3) ? a[2] + 0 : 0; hi = (n >= 3) ? a[3] + 0 : 1000
          v = sprintf("%.2f", lo + rand() * (hi - lo))
        } else {
          v = rstr((n >= 2) ? a[2] + 0 : 8)
        }
        out = out substr(line, 1, RSTART - 1) v
        line = substr(line, RSTART + RLENGTH)
      }
      print out line
    }'
}

find_python() {
  local p
  for p in python3 python; do
    if command -v "$p" >/dev/null 2>&1 && "$p" -c 'import json' >/dev/null 2>&1; then echo "$p"; return; fi
  done
}
HAS_JQ=false; command -v jq >/dev/null 2>&1 && HAS_JQ=true
PY=""; $HAS_JQ || PY="$(find_python)"
$HAS_JQ || [ -n "$PY" ] || echo "Warning: neither jq nor python found; sending without JSON validation." >&2

# Validates JSON on stdin and prints each document compacted onto one line.
compact_json() {
  if $HAS_JQ; then
    jq -c .
  elif [ -n "$PY" ]; then
    "$PY" -c '
import json, sys
text, dec, pos, out = sys.stdin.read(), json.JSONDecoder(), 0, []
while True:
    while pos < len(text) and text[pos].isspace(): pos += 1
    if pos >= len(text): break
    try:
        obj, pos = dec.raw_decode(text, pos)
    except ValueError as e:
        sys.exit("invalid JSON: %s" % e)
    out.append(json.dumps(obj, separators=(",", ":"), ensure_ascii=False))
print("\n".join(out))
'
  else
    tr -d '\r\n'; echo
  fi
}

# One round of messages: placeholders filled, validated, compacted, and prefixed with the key if there is one.
render_messages() {
  local payload
  payload="$(printf '%s\n' "$TEMPLATE" | fill_placeholders | compact_json)" || return 1
  if [ -n "$KEY" ]; then
    printf '%s\n' "$payload" | while IFS= read -r line; do printf '%s\t%s\n' "$KEY" "$line"; done
  else
    printf '%s\n' "$payload"
  fi
}

# Render once up front so a broken template fails before anything is sent.
FIRST_ROUND="$(render_messages)" || die "invalid JSON in $FILE (after filling placeholders)"
COUNT="$(printf '%s\n' "$FIRST_ROUND" | grep -c .)"

# ---- Ask how often to send ---------------------------------------------------------------------------------
if [ -z "$INTERVAL" ]; then
  if [ -t 0 ] && ! $DRY_RUN; then
    while true; do
      read -r -p "Send every how many seconds? (0 or Enter = send once): " INTERVAL
      INTERVAL="${INTERVAL:-0}"
      is_interval "$INTERVAL" && break
      echo "Please enter a number of seconds, e.g. 5 or 0.5." >&2
    done
  else
    INTERVAL=0
  fi
fi
is_interval "$INTERVAL" || die "the interval must be a number of seconds, e.g. 5 or 0.5 (got '$INTERVAL')"
REPEAT=false
[[ "$INTERVAL" =~ ^0+([.]0+)?$ ]] || REPEAT=true
is_interval "$RANDOM_DELAY" || die "the random delay must be a number of seconds, e.g. 5 or 0.5 (got '$RANDOM_DELAY')"
HAS_RANDOM_DELAY=false
[[ "$RANDOM_DELAY" =~ ^0+([.]0+)?$ ]] || HAS_RANDOM_DELAY=true
if $HAS_RANDOM_DELAY && ! $REPEAT; then
  echo "Note: -r only applies when sending repeatedly; ignored for a single send." >&2
fi

# Seconds to wait before the next round: the interval plus, with -r, a random 0..RANDOM_DELAY.
next_wait() {
  if $HAS_RANDOM_DELAY; then
    awk -v seed="$RANDOM$RANDOM" -v base="$INTERVAL" -v extra="$RANDOM_DELAY" \
      'BEGIN { srand(seed); printf "%.2f", base + rand() * extra }'
  else
    printf '%s' "$INTERVAL"
  fi
}

# ---- Pick a producer ---------------------------------------------------------------------------------------
find_console_producer() {
  local c dir candidates=()
  for c in kafka-console-producer.sh kafka-console-producer; do
    if command -v "$c" >/dev/null 2>&1; then command -v "$c"; return; fi
  done
  [ -z "${KAFKA_HOME:-}" ] || candidates+=("$KAFKA_HOME/bin")
  [ -z "${CONFLUENT_HOME:-}" ] || candidates+=("$CONFLUENT_HOME/bin")
  # The script's parent folders and their direct subfolders, so a copy placed in a Kafka install finds its
  # bin/: /opt/kafka/producer -> /opt/kafka/bin, or /opt/kafka/confluent-8.1.0/bin for Confluent Platform.
  dir="$SCRIPT_DIR"
  while [ "$dir" != "/" ] && [ -n "$dir" ]; do
    dir="$(dirname "$dir")"
    candidates+=("$dir/bin" "$dir"/*/bin)
  done
  # Common install locations (globs that match nothing are skipped by the -x test below).
  candidates+=(/opt/kafka/bin /opt/kafka/*/bin /opt/kafka_*/bin /opt/kafka-*/bin /opt/confluent*/bin \
               /usr/local/kafka/bin /usr/local/kafka_*/bin /usr/local/confluent*/bin \
               "$HOME"/kafka/bin "$HOME"/kafka_*/bin "$HOME"/confluent*/bin)
  # Apache Kafka ships kafka-console-producer.sh; Confluent Platform ships kafka-console-producer.
  for dir in "${candidates[@]}"; do
    for c in kafka-console-producer.sh kafka-console-producer; do
      if [ -f "$dir/$c" ] && [ -x "$dir/$c" ]; then echo "$dir/$c"; return; fi
    done
  done
}

KCAT="$(command -v kcat 2>/dev/null || command -v kafkacat 2>/dev/null || true)"
CONSOLE="$(find_console_producer)"

if [ "$TOOL" = "auto" ]; then
  if [ -n "$KCAT" ]; then TOOL=kcat
  elif [ -n "$CONSOLE" ]; then TOOL=console
  elif command -v docker >/dev/null 2>&1; then TOOL=docker
  else die "no Kafka producer found. Looked for kcat, kafka-console-producer.sh on the PATH, in KAFKA_HOME/bin, in folders around this script and in common install locations, and for Docker. Fix: run with KAFKA_HOME=/path/to/kafka (the folder that contains bin/), add its bin/ to PATH, or install kcat"
  fi
fi

KEY_ARGS=()
case "$TOOL" in
  kcat)
    [ -n "$KCAT" ] || die "kcat/kafkacat not found"
    [ -z "$KEY" ] || KEY_ARGS=(-K $'\t')
    CMD=("$KCAT" -P -b "$BROKER" -t "$TOPIC" "${KEY_ARGS[@]}")
    ;;
  console|docker)
    [ -z "$KEY" ] || KEY_ARGS=(--property parse.key=true --property $'key.separator=\t')
    if [ "$TOOL" = console ]; then
      [ -n "$CONSOLE" ] || die "kafka-console-producer not found (add Kafka's bin/ to PATH or set KAFKA_HOME)"
      CMD=("$CONSOLE")
      TARGET_BROKER="$BROKER"
    else
      command -v docker >/dev/null 2>&1 || die "docker not found"
      # Inside a container "localhost" is the container itself. Linux uses the host network directly;
      # Docker Desktop (Windows/macOS) reaches the host as host.docker.internal.
      if [ "$(uname -s)" = Linux ]; then
        DOCKER_NET=(--network host)
        TARGET_BROKER="$BROKER"
      else
        DOCKER_NET=()
        TARGET_BROKER="$(printf '%s' "$BROKER" | sed -E 's/(^|,)(localhost|127\.0\.0\.1)(:|,|$)/\1host.docker.internal\3/g')"
      fi
      # MSYS_NO_PATHCONV stops Git Bash on Windows from rewriting the container path.
      CMD=(env MSYS_NO_PATHCONV=1 docker run --rm -i "${DOCKER_NET[@]}" "$KAFKA_IMAGE" /opt/kafka/bin/kafka-console-producer.sh)
    fi
    CMD+=(--bootstrap-server "$TARGET_BROKER" --topic "$TOPIC" "${KEY_ARGS[@]}")
    ;;
  *) die "unknown tool '$TOOL' (use kcat, console or docker)" ;;
esac

# ---- Send --------------------------------------------------------------------------------------------------
echo "File:   $FILE ($COUNT message(s) per round)" >&2
echo "Target: topic '$TOPIC' on $BROKER using $TOOL${KEY:+, key '$KEY'}" >&2
if $REPEAT && $HAS_RANDOM_DELAY; then
  echo "Mode:   every ${INTERVAL}s + random 0-${RANDOM_DELAY}s until Ctrl+C" >&2
elif $REPEAT; then echo "Mode:   every ${INTERVAL}s until Ctrl+C" >&2; else echo "Mode:   once" >&2; fi

if $DRY_RUN; then
  printf 'Command:' >&2; printf ' %q' "${CMD[@]}" >&2; echo >&2
  printf '%s\n' "$FIRST_ROUND"
  exit 0
fi

if ! $REPEAT; then
  printf '%s\n' "$FIRST_ROUND" | "${CMD[@]}"
  echo "Done." >&2
  exit 0
fi

# One long-running producer fed through a pipe, so a new JVM/container isn't started for every message.
# Placeholders are re-filled every round, so each message gets new values.
trap 'echo >&2; echo "Stopped." >&2; exit 0' INT TERM
produce_every_interval() {
  local round=0 messages="$FIRST_ROUND" wait
  while true; do
    round=$((round + 1))
    wait="$(next_wait)"
    if [ "$round" -gt 1 ] && ! messages="$(render_messages)"; then
      echo "[$(date +%H:%M:%S)] round $round skipped: invalid JSON after filling placeholders; next in ${wait}s" >&2
      sleep "$wait"
      continue
    fi
    printf '%s\n' "$messages"
    echo "[$(date +%H:%M:%S)] round $round: $COUNT message(s) handed to the producer; next in ${wait}s" >&2
    sleep "$wait"
  done
}
produce_every_interval | "${CMD[@]}"
