#!/usr/bin/env bash
# Keeps Docker from being swamped by leaked test containers.
#
# Runs on SessionStart, SubagentStop and Stop (sweep only) and before every Bash command
# (PreToolUse). It only ever touches containers this project starts: the spec runtime
# containers (label com.mridang.openapi.testcontainer=true), the mock server and proxy the
# generated tests start (images mridang/chasm and ubuntu/squid), and containers started by
# hand with --label parity.guard=1.
#
#   sweep     remove our containers running longer than MAX_AGE_MIN (hand-started ones
#             after HAND_AGE_MIN), and the maven-started ones when no maven process is
#             alive to own them
#   capacity  before a command that starts containers (mvn, docker run), wait until fewer
#             than MAX_RUNNING of ours are running; refuse after WAIT_SEC
#   docker run without --rm and --label parity.guard=1 is refused, so hand-started
#             containers can always be found and cleaned up
set -uo pipefail

MAX_RUNNING="${PARITY_MAX_CONTAINERS:-8}"
MAX_AGE_MIN="${PARITY_MAX_CONTAINER_AGE_MIN:-60}"
HAND_AGE_MIN=30
# The mock server and proxy live only as long as one test suite; Testcontainers' reaper is
# off inside the spec runtimes, so these outlive a killed run and pile up fastest.
HELPER_AGE_MIN=25
WAIT_SEC=540
LOG="${TMPDIR:-/tmp}/parity-docker-guard.log"

input="$(cat)"
event="$(jq -r '.hook_event_name // empty' <<< "$input")"
command="$(jq -r '.tool_input.command // empty' <<< "$input")"

log() { echo "$(date '+%F %T') $*" >> "$LOG"; }

deny() {
  log "refused: $1"
  jq -n --arg r "$1" '{hookSpecificOutput: {hookEventName: "PreToolUse", permissionDecision: "deny", permissionDecisionReason: $r}}'
  exit 0
}

ours() {
  {
    docker ps -q --filter label=com.mridang.openapi.testcontainer=true
    docker ps -q --filter label=parity.guard=1
    docker ps -q --filter ancestor=mridang/chasm:1.3.0
    docker ps -q --filter ancestor=ubuntu/squid:6.10-24.10_beta
  } 2> /dev/null | sort -u
}

sweep() {
  local id started age limit hand maven_alive
  maven_alive=0
  pgrep -f 'plexus-classworlds|surefire|failsafe' > /dev/null 2>&1 && maven_alive=1
  for id in $(ours); do
    started="$(docker inspect -f '{{.State.StartedAt}}' "$id" 2> /dev/null | cut -c1-19)"
    [ -n "$started" ] || continue
    age=$(( ($(date -u +%s) - $(date -u -j -f '%Y-%m-%dT%H:%M:%S' "$started" +%s 2> /dev/null || date -u -d "$started" +%s)) / 60 ))
    hand="$(docker inspect -f '{{index .Config.Labels "parity.guard"}}' "$id" 2> /dev/null)"
    image="$(docker inspect -f '{{.Config.Image}}' "$id" 2> /dev/null)"
    limit="$MAX_AGE_MIN"
    [ "$hand" = "1" ] && limit="$HAND_AGE_MIN"
    case "$image" in mridang/chasm* | ubuntu/squid*) limit="$HELPER_AGE_MIN" ;; esac
    if [ "$age" -ge "$limit" ]; then
      docker rm -f "$id" > /dev/null 2>&1 && log "removed $id (running ${age} min)"
    elif [ "$maven_alive" -eq 0 ] && [ "$hand" != "1" ]; then
      docker rm -f "$id" > /dev/null 2>&1 && log "removed $id (no maven process owns it)"
    fi
  done
  # Leaked test networks exhaust Docker's address pools ("all predefined address pools
  # have been fully subnetted"); prune only Testcontainers' networks that nothing uses.
  docker network prune -f --filter label=org.testcontainers=true > /dev/null 2>&1
}

if ! timeout 20 docker ps -q > /dev/null 2>&1; then
  [ "$event" = "PreToolUse" ] || exit 0
  case "$command" in
    *mvn*verify* | *ci-local.sh\ check* | *docker\ run*)
      deny "Docker is not responding (docker ps timed out). Do not retry in a loop: stop this task and report that Docker is wedged; the docker-guard agent knows how to recover it." ;;
  esac
  exit 0
fi

if [ "$event" != "PreToolUse" ]; then
  sweep
  exit 0
fi

case "$command" in
  *docker\ run* | *docker\ container\ run*)
    if ! grep -q -- '--rm' <<< "$command" || ! grep -q -- '--label parity.guard=1' <<< "$command"; then
      deny "Start hand-run containers with: docker run --rm --label parity.guard=1 ... so the guard can clean them up if this task is interrupted."
    fi ;;
  *mvn* | *ci-local.sh\ check*) ;;
  *) exit 0 ;;
esac

sweep
waited=0
while [ "$(ours | wc -l | tr -d ' ')" -ge "$MAX_RUNNING" ]; do
  if [ "$waited" -ge "$WAIT_SEC" ]; then
    deny "Docker is full: $MAX_RUNNING or more test containers are running. Wait for other runs to finish, then retry this command once."
  fi
  sleep 15
  waited=$((waited + 15))
  sweep
done
[ "$waited" -gt 0 ] && log "waited ${waited}s for a free slot: $command"
exit 0
