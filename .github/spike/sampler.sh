#!/usr/bin/env bash
# Spike only: samples runner utilisation every 5 seconds until killed.
out="${1:?output dir}"
mkdir -p "$out"
samples="$out/samples.csv"
echo "ts,cpu_user,cpu_nice,cpu_sys,cpu_idle,cpu_iowait,cpu_irq,cpu_softirq,cpu_steal,procs_running,procs_blocked,load1,load5,load15,mem_total_kb,mem_avail_kb,swap_used_kb,disk_rd_sectors,disk_wr_sectors,disk_io_ms,net_rx_bytes,net_tx_bytes,containers,java_procs,mvn_procs" > "$samples"

(
  while true; do
    ts=$(date +%s)
    docker stats --no-stream --format '{{.Name}},{{.CPUPerc}},{{.MemUsage}},{{.NetIO}},{{.BlockIO}}' 2>/dev/null | sed "s/^/$ts,/" >> "$out/docker-stats.csv"
    sleep 5
  done
) &

(
  mkdir -p "$out/dumps"
  while true; do
    ts=$(date +%s)
    for pid in $(pgrep java || true); do
      jcmd "$pid" Thread.print > "$out/dumps/$ts-$pid.txt" 2>&1 || true
    done
    sleep 15
  done
) &

while true; do
  ts=$(date +%s)
  cpu=$(awk '/^cpu /{print $2","$3","$4","$5","$6","$7","$8","$9}' /proc/stat)
  pr=$(awk '/^procs_running/{print $2}' /proc/stat)
  pb=$(awk '/^procs_blocked/{print $2}' /proc/stat)
  load=$(awk '{print $1","$2","$3}' /proc/loadavg)
  mem=$(awk '/MemTotal/{t=$2}/MemAvailable/{a=$2}END{print t","a}' /proc/meminfo)
  swap=$(awk '/SwapTotal/{t=$2}/SwapFree/{f=$2}END{print t-f}' /proc/meminfo)
  disk=$(awk '$3 ~ /^(sd[a-z]|vd[a-z]|nvme[0-9]+n[0-9]+)$/ {r+=$6;w+=$10;m+=$13}END{print r+0","w+0","m+0}' /proc/diskstats)
  net=$(awk 'NR>2 && $1 !~ /^(lo|docker|br-|veth)/ {rx+=$2;tx+=$10}END{print rx+0","tx+0}' /proc/net/dev)
  ctr=$(docker ps -q 2>/dev/null | wc -l | tr -d ' ')
  jv=$(pgrep -c java || true)
  mv=$(pgrep -fc 'maven|mvn' || true)
  echo "$ts,$cpu,$pr,$pb,$load,$mem,$swap,$disk,$net,$ctr,$jv,$mv" >> "$samples"
  sleep 5
done
