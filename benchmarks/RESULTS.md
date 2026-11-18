# Benchmark results, 2026-11-18

Baseline run of `benchmarks/run.sh` against the committed golden SDKs at commit 1439c2a. This is a single run, not a reference machine: Apple M1 Max (10 cores) running Docker Desktop (Linux arm64 VM, 10 vCPU, 16 GB), with other builds running on the host at the same time (load average 14 to 18 on 10 cores while go, java, node, python and rust ran; php and csharp ran later). Each row is the median of 5 rounds, each round a timed batch of about 0.7 s (go: `-benchtime 1s`); "range" is the fastest and slowest round and "spread" is (max - min) / median. A spread above about 30% means the row is noise-dominated: read the ratio as "within noise" and not as a difference. Bytes and allocations per operation are measured for go, java (bytes), csharp (bytes) and rust; node, python and php do not report them. Payloads: small is one `Tag` (27 bytes), medium one `Pet` (459 bytes, 3 tags, 3 photo URLs, a nested category), large an array of 200 `Pet` (96 KB). Rebuild the numbers with `benchmarks/run.sh`.

### go

| model | op | implementation | median | range (min-max) | spread | vs generated | bytes/op | allocs/op |
|---|---|---|---|---|---|---|---|---|
| small | decode | generated | 2.1 us | 2.0 us - 2.2 us | 7% | 1.00x | 1080 | 23 |
| small | decode | stdlib | 3.2 us | 2.1 us - 4.8 us | 85% | 1.52x | 1080 | 23 |
| small | decode | easyjson | 155 ns | 150 ns - 180 ns | 19% | 0.07x | 48 | 4 |
| small | decode | jsonv2 | 2.3 us | 1.6 us - 2.4 us | 34% | 1.09x | 528 | 11 |
| small | encode | generated | 509 ns | 445 ns - 558 ns | 22% | 1.00x | 240 | 5 |
| small | encode | stdlib | 520 ns | 458 ns - 718 ns | 50% | 1.02x | 160 | 4 |
| small | encode | easyjson | 284 ns | 184 ns - 449 ns | 93% | 0.56x | 128 | 1 |
| small | encode | jsonv2 | 682 ns | 662 ns - 1.8 us | 165% | 1.34x | 536 | 9 |
| medium | decode | generated | 34.9 us | 33.2 us - 43.9 us | 31% | 1.00x | 12117 | 260 |
| medium | decode | stdlib | 41.1 us | 32.7 us - 51.0 us | 45% | 1.18x | 12117 | 260 |
| medium | decode | easyjson | 1.6 us | 1.5 us - 1.8 us | 13% | 0.05x | 768 | 33 |
| medium | decode | jsonv2 | 29.3 us | 24.4 us - 35.3 us | 37% | 0.84x | 8036 | 133 |
| medium | encode | generated | 5.6 us | 5.4 us - 6.8 us | 25% | 1.00x | 2194 | 30 |
| medium | encode | stdlib | 5.9 us | 5.8 us - 6.3 us | 8% | 1.06x | 2146 | 29 |
| medium | encode | easyjson | 3.3 us | 2.9 us - 3.6 us | 22% | 0.59x | 1080 | 5 |
| medium | encode | jsonv2 | 10.7 us | 9.0 us - 20.5 us | 108% | 1.91x | 7008 | 76 |
| large | decode | generated | 10.33 ms | 7.82 ms - 10.54 ms | 26% | 1.00x | 2566514 | 51433 |
| large | decode | stdlib | 7.38 ms | 6.74 ms - 8.37 ms | 22% | 0.71x | 2447858 | 51017 |
| large | decode | easyjson | 456.5 us | 377.6 us - 812.5 us | 95% | 0.04x | 199633 | 6410 |
| large | decode | jsonv2 | 6.90 ms | 4.82 ms - 8.33 ms | 51% | 0.67x | 1721202 | 27854 |
| large | encode | generated | 2.59 ms | 1.88 ms - 2.81 ms | 36% | 1.00x | 469977 | 5607 |
| large | encode | stdlib | 1.34 ms | 1.25 ms - 1.35 ms | 7% | 0.52x | 460328 | 5604 |
| large | encode | easyjson | 342.3 us | 314.4 us - 432.4 us | 34% | 0.13x | 100123 | 13 |
| large | encode | jsonv2 | 1.65 ms | 1.60 ms - 1.97 ms | 23% | 0.64x | 1555180 | 16109 |

### java

| model | op | implementation | median | range (min-max) | spread | vs generated | bytes/op | allocs/op |
|---|---|---|---|---|---|---|---|---|
| small | decode | generated | 270 ns | 268 ns - 322 ns | 20% | 1.00x | 720 | - |
| small | decode | blackbird | 260 ns | 254 ns - 265 ns | 4% | 0.96x | 720 | - |
| small | decode | afterburner | 262 ns | 260 ns - 276 ns | 6% | 0.97x | 720 | - |
| small | decode | handstream | 249 ns | 230 ns - 275 ns | 18% | 0.92x | 624 | - |
| small | encode | generated | 162 ns | 154 ns - 185 ns | 19% | 1.00x | 520 | - |
| small | encode | blackbird | 150 ns | 149 ns - 154 ns | 3% | 0.93x | 520 | - |
| small | encode | afterburner | 142 ns | 140 ns - 202 ns | 44% | 0.87x | 520 | - |
| small | encode | handstream | 150 ns | 144 ns - 181 ns | 24% | 0.93x | 512 | - |
| medium | decode | generated | 2.5 us | 2.5 us - 3.3 us | 31% | 1.00x | 3752 | - |
| medium | decode | blackbird | 2.5 us | 2.4 us - 2.6 us | 8% | 0.97x | 3752 | - |
| medium | decode | afterburner | 2.4 us | 2.4 us - 2.6 us | 6% | 0.96x | 3752 | - |
| medium | decode | handstream | 2.3 us | 2.2 us - 2.4 us | 9% | 0.91x | 3352 | - |
| medium | encode | generated | 823 ns | 721 ns - 964 ns | 30% | 1.00x | 1216 | - |
| medium | encode | blackbird | 738 ns | 734 ns - 755 ns | 3% | 0.90x | 1216 | - |
| medium | encode | afterburner | 725 ns | 665 ns - 797 ns | 18% | 0.88x | 1216 | - |
| medium | encode | handstream | 870 ns | 787 ns - 940 ns | 18% | 1.06x | 1552 | - |
| large | decode | generated | 489.8 us | 470.1 us - 581.8 us | 23% | 1.00x | 622976 | - |
| large | decode | blackbird | 467.0 us | 464.7 us - 484.0 us | 4% | 0.95x | 622976 | - |
| large | decode | afterburner | 472.1 us | 467.7 us - 474.4 us | 1% | 0.96x | 622976 | - |
| large | decode | handstream | 443.9 us | 419.8 us - 502.1 us | 19% | 0.91x | 557128 | - |
| large | encode | generated | 263.4 us | 164.2 us - 313.8 us | 57% | 1.00x | 355968 | - |
| large | encode | blackbird | 166.7 us | 164.3 us - 168.3 us | 2% | 0.63x | 355968 | - |
| large | encode | afterburner | 163.6 us | 160.9 us - 174.4 us | 8% | 0.62x | 355968 | - |
| large | encode | handstream | 157.5 us | 156.9 us - 211.5 us | 35% | 0.60x | 365440 | - |

### node

| model | op | implementation | median | range (min-max) | spread | vs generated | bytes/op | allocs/op |
|---|---|---|---|---|---|---|---|---|
| small | decode | generated | 3.4 us | 3.3 us - 4.7 us | 39% | 1.00x | - | - |
| small | decode | manual | 403 ns | 398 ns - 601 ns | 50% | 0.12x | - | - |
| small | decode | native | 205 ns | 203 ns - 208 ns | 2% | 0.06x | - | - |
| small | encode | generated | 790 ns | 714 ns - 1.1 us | 50% | 1.00x | - | - |
| small | encode | manual | 174 ns | 164 ns - 186 ns | 12% | 0.22x | - | - |
| small | encode | native | 161 ns | 157 ns - 196 ns | 24% | 0.20x | - | - |
| medium | decode | generated | 32.0 us | 29.2 us - 37.7 us | 26% | 1.00x | - | - |
| medium | decode | manual | 3.0 us | 2.7 us - 4.2 us | 48% | 0.09x | - | - |
| medium | decode | native | 1.2 us | 1.2 us - 1.3 us | 6% | 0.04x | - | - |
| medium | encode | generated | 8.1 us | 7.4 us - 9.6 us | 26% | 1.00x | - | - |
| medium | encode | manual | 860 ns | 847 ns - 878 ns | 4% | 0.11x | - | - |
| medium | encode | native | 800 ns | 788 ns - 834 ns | 6% | 0.10x | - | - |
| large | decode | generated | 5.95 ms | 5.83 ms - 6.72 ms | 15% | 1.00x | - | - |
| large | decode | manual | 569.0 us | 552.6 us - 952.5 us | 70% | 0.10x | - | - |
| large | decode | native | 250.1 us | 238.5 us - 318.9 us | 32% | 0.04x | - | - |
| large | encode | generated | 1.53 ms | 1.51 ms - 1.95 ms | 29% | 1.00x | - | - |
| large | encode | manual | 186.9 us | 184.1 us - 208.2 us | 13% | 0.12x | - | - |
| large | encode | native | 178.3 us | 168.2 us - 262.2 us | 53% | 0.12x | - | - |

### python

| model | op | implementation | median | range (min-max) | spread | vs generated | bytes/op | allocs/op |
|---|---|---|---|---|---|---|---|---|
| small | decode | generated | 8.6 us | 6.9 us - 9.6 us | 32% | 1.00x | - | - |
| small | decode | pydantic | 1.2 us | 1.0 us - 1.3 us | 25% | 0.14x | - | - |
| small | decode | manual | 1.3 us | 1.2 us - 1.5 us | 24% | 0.15x | - | - |
| small | decode | orjson | 398 ns | 358 ns - 483 ns | 31% | 0.05x | - | - |
| small | decode | jsonfloor | 1.2 us | 1.1 us - 1.7 us | 56% | 0.13x | - | - |
| small | encode | generated | 5.5 us | 4.8 us - 6.0 us | 21% | 1.00x | - | - |
| small | encode | pydantic | 986 ns | 877 ns - 1.3 us | 42% | 0.18x | - | - |
| small | encode | manual | 2.2 us | 2.1 us - 2.7 us | 28% | 0.40x | - | - |
| small | encode | orjson | 370 ns | 348 ns - 411 ns | 17% | 0.07x | - | - |
| medium | decode | generated | 31.2 us | 26.9 us - 65.4 us | 123% | 1.00x | - | - |
| medium | decode | pydantic | 7.4 us | 6.4 us - 8.3 us | 25% | 0.24x | - | - |
| medium | decode | manual | 4.9 us | 4.6 us - 5.5 us | 17% | 0.16x | - | - |
| medium | decode | orjson | 2.8 us | 2.7 us - 3.2 us | 20% | 0.09x | - | - |
| medium | decode | jsonfloor | 3.8 us | 3.5 us - 4.2 us | 16% | 0.12x | - | - |
| medium | encode | generated | 9.9 us | 7.8 us - 14.0 us | 62% | 1.00x | - | - |
| medium | encode | pydantic | 3.7 us | 2.9 us - 4.9 us | 54% | 0.37x | - | - |
| medium | encode | manual | 6.7 us | 6.0 us - 9.6 us | 54% | 0.67x | - | - |
| medium | encode | orjson | 1.9 us | 1.7 us - 2.2 us | 28% | 0.19x | - | - |
| large | decode | generated | 5.92 ms | 4.54 ms - 7.09 ms | 43% | 1.00x | - | - |
| large | decode | pydantic | 2.22 ms | 2.02 ms - 2.55 ms | 24% | 0.38x | - | - |
| large | decode | manual | 1.11 ms | 996.9 us - 1.21 ms | 19% | 0.19x | - | - |
| large | decode | orjson | 908.4 us | 800.1 us - 1.30 ms | 55% | 0.15x | - | - |
| large | decode | jsonfloor | 657.4 us | 623.7 us - 826.2 us | 31% | 0.11x | - | - |
| large | encode | generated | 3.97 ms | 3.33 ms - 4.65 ms | 33% | 1.00x | - | - |
| large | encode | pydantic | 929.4 us | 724.0 us - 1.21 ms | 52% | 0.23x | - | - |
| large | encode | manual | 1.12 ms | 954.8 us - 1.69 ms | 66% | 0.28x | - | - |
| large | encode | orjson | 501.2 us | 429.5 us - 999.2 us | 114% | 0.13x | - | - |

### rust

| model | op | implementation | median | range (min-max) | spread | vs generated | bytes/op | allocs/op |
|---|---|---|---|---|---|---|---|---|
| small | decode | generated | 140 ns | 140 ns - 170 ns | 22% | 1.00x | 8 | 1 |
| small | decode | serdejson | 88 ns | 86 ns - 96 ns | 12% | 0.63x | 8 | 1 |
| small | decode | simdjson | 245 ns | 236 ns - 253 ns | 7% | 1.75x | 889 | 8 |
| small | decode | sonic | 111 ns | 107 ns - 112 ns | 4% | 0.79x | 8 | 1 |
| small | encode | generated | 67 ns | 64 ns - 76 ns | 18% | 1.00x | 128 | 1 |
| small | encode | serdejson | 58 ns | 57 ns - 63 ns | 10% | 0.85x | 128 | 1 |
| small | encode | simdjson | 60 ns | 59 ns - 62 ns | 6% | 0.90x | 512 | 1 |
| small | encode | sonic | 53 ns | 52 ns - 56 ns | 8% | 0.78x | 128 | 1 |
| medium | decode | generated | 1.8 us | 1.7 us - 1.8 us | 6% | 1.00x | 484 | 14 |
| medium | decode | serdejson | 1.1 us | 1.0 us - 1.2 us | 17% | 0.59x | 484 | 14 |
| medium | decode | simdjson | 1.1 us | 1.1 us - 1.1 us | 6% | 0.61x | 7866 | 24 |
| medium | decode | sonic | 952 ns | 912 ns - 985 ns | 8% | 0.53x | 484 | 14 |
| medium | encode | generated | 697 ns | 670 ns - 762 ns | 13% | 1.00x | 1676 | 11 |
| medium | encode | serdejson | 585 ns | 573 ns - 624 ns | 9% | 0.84x | 1286 | 7 |
| medium | encode | simdjson | 483 ns | 476 ns - 557 ns | 17% | 0.69x | 902 | 5 |
| medium | encode | sonic | 531 ns | 505 ns - 552 ns | 9% | 0.76x | 2310 | 8 |
| large | decode | generated | 428.0 us | 415.0 us - 447.9 us | 8% | 1.00x | 261680 | 2807 |
| large | decode | serdejson | 274.5 us | 266.6 us - 301.1 us | 13% | 0.64x | 261680 | 2807 |
| large | decode | simdjson | 197.4 us | 196.9 us - 226.0 us | 15% | 0.46x | 1468757 | 2810 |
| large | decode | sonic | 226.8 us | 221.0 us - 228.5 us | 3% | 0.53x | 261680 | 2807 |
| large | encode | generated | 120.9 us | 119.8 us - 179.1 us | 49% | 1.00x | 419756 | 1611 |
| large | encode | serdejson | 96.6 us | 96.0 us - 108.4 us | 13% | 0.80x | 340886 | 811 |
| large | encode | simdjson | 96.0 us | 95.4 us - 98.0 us | 3% | 0.79x | 340502 | 809 |
| large | encode | sonic | 79.2 us | 76.7 us - 82.2 us | 7% | 0.66x | 340886 | 811 |

### php

| model | op | implementation | median | range (min-max) | spread | vs generated | bytes/op | allocs/op |
|---|---|---|---|---|---|---|---|---|
| small | decode | generated | 18.5 us | 17.9 us - 18.5 us | 4% | 1.00x | - | - |
| small | decode | manual | 385 ns | 377 ns - 397 ns | 5% | 0.02x | - | - |
| small | decode | jsonfloor | 262 ns | 255 ns - 306 ns | 20% | 0.01x | - | - |
| small | encode | generated | 4.1 us | 4.0 us - 4.3 us | 9% | 1.00x | - | - |
| small | encode | manual | 197 ns | 195 ns - 202 ns | 3% | 0.05x | - | - |
| medium | decode | generated | 133.9 us | 130.7 us - 138.4 us | 6% | 1.00x | - | - |
| medium | decode | manual | 5.1 us | 5.0 us - 5.2 us | 2% | 0.04x | - | - |
| medium | decode | jsonfloor | 2.2 us | 2.1 us - 2.3 us | 7% | 0.02x | - | - |
| medium | encode | generated | 35.8 us | 35.6 us - 36.4 us | 2% | 1.00x | - | - |
| medium | encode | manual | 2.5 us | 2.5 us - 2.6 us | 6% | 0.07x | - | - |
| large | decode | generated | 28.73 ms | 28.31 ms - 28.90 ms | 2% | 1.00x | - | - |
| large | decode | manual | 1.12 ms | 1.11 ms - 1.24 ms | 12% | 0.04x | - | - |
| large | decode | jsonfloor | 438.6 us | 421.7 us - 463.1 us | 9% | 0.02x | - | - |
| large | encode | generated | 7.33 ms | 7.26 ms - 7.40 ms | 2% | 1.00x | - | - |
| large | encode | manual | 538.2 us | 495.2 us - 671.5 us | 33% | 0.07x | - | - |

### csharp

| model | op | implementation | median | range (min-max) | spread | vs generated | bytes/op | allocs/op |
|---|---|---|---|---|---|---|---|---|
| small | decode | generated | 264 ns | 224 ns - 766 ns | 205% | 1.00x | 80 | - |
| small | decode | sourcegen | 234 ns | 211 ns - 322 ns | 47% | 0.89x | 160 | - |
| small | encode | generated | 221 ns | 151 ns - 666 ns | 233% | 1.00x | 224 | - |
| small | encode | sourcegen | 157 ns | 148 ns - 221 ns | 47% | 0.71x | 224 | - |
| medium | decode | generated | 2.7 us | 2.2 us - 3.0 us | 30% | 1.00x | 1944 | - |
| medium | decode | sourcegen | 2.6 us | 2.1 us - 3.8 us | 68% | 0.94x | 2400 | - |
| medium | encode | generated | 1.9 us | 1.3 us - 2.1 us | 42% | 1.00x | 3240 | - |
| medium | encode | sourcegen | 1.4 us | 1.3 us - 2.0 us | 48% | 0.76x | 3240 | - |
| large | decode | generated | 481.2 us | 412.2 us - 785.8 us | 78% | 1.00x | 300280 | - |
| large | decode | sourcegen | 551.9 us | 435.9 us - 655.8 us | 40% | 1.15x | 391408 | - |
| large | encode | generated | 505.5 us | 283.8 us - 880.7 us | 118% | 1.00x | 598520 | - |
| large | encode | sourcegen | 481.0 us | 314.2 us - 712.1 us | 83% | 0.95x | 598504 | - |
