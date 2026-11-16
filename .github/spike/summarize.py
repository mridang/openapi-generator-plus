#!/usr/bin/env python3
"""Spike only: turns samples.csv into a per-minute table."""
import csv
import collections
import sys

d = sys.argv[1]
rows = list(csv.DictReader(open(d + "/samples.csv")))
rows = [{k: float(v) for k, v in r.items()} for r in rows if r["ts"]]
t0 = rows[0]["ts"]
buckets = collections.defaultdict(list)
for a, b in zip(rows, rows[1:]):
    buckets[int((b["ts"] - t0) // 60)].append((a, b))
print("| min | cpu busy % | iowait % | runq | load1 | mem avail GB | disk rd MB/s | disk wr MB/s | net rx MB/s | net tx MB/s | containers | java |")
print("|---|---|---|---|---|---|---|---|---|---|---|---|")
for m in sorted(buckets):
    ps = buckets[m]
    a, b = ps[0][0], ps[-1][1]
    dt = b["ts"] - a["ts"] or 1
    keys = ["cpu_user", "cpu_nice", "cpu_sys", "cpu_idle", "cpu_iowait", "cpu_irq", "cpu_softirq", "cpu_steal"]
    dj = {k: b[k] - a[k] for k in keys}
    tot = sum(dj.values()) or 1
    busy = 100 * (tot - dj["cpu_idle"] - dj["cpu_iowait"]) / tot
    iow = 100 * dj["cpu_iowait"] / tot

    def avg(k):
        return sum(x[1][k] for x in ps) / len(ps)

    print("| %d | %.0f | %.0f | %.1f | %.1f | %.1f | %.1f | %.1f | %.1f | %.1f | %.1f | %.1f |" % (
        m, busy, iow, avg("procs_running"), avg("load1"), avg("mem_avail_kb") / 1048576,
        (b["disk_rd_sectors"] - a["disk_rd_sectors"]) * 512 / 1e6 / dt,
        (b["disk_wr_sectors"] - a["disk_wr_sectors"]) * 512 / 1e6 / dt,
        (b["net_rx_bytes"] - a["net_rx_bytes"]) / 1e6 / dt,
        (b["net_tx_bytes"] - a["net_tx_bytes"]) / 1e6 / dt,
        avg("containers"), avg("java_procs")))
