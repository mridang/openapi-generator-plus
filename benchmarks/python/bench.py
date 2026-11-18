"""Runs against the generated python SDK. Prints rows:
lang impl model op ns_per_op bytes_per_op allocs_per_op (one per round; bytes/allocs are '-' on python)."""
import gc
import json
import os
import time
from typing import List, Optional

import orjson

from petstore_client.models.pet import Pet
from petstore_client.models.tag import Tag
from petstore_client.object_serializer import ObjectSerializer

ROUNDS = int(os.environ.get("ROUNDS", "5"))
TARGET_S = float(os.environ.get("TARGET_MS", "700")) / 1000
TEXT = {m: open(f"/fixtures/{m}.json", encoding="utf-8").read() for m in ("small", "medium", "large")}
sink = None
serializer = ObjectSerializer()


class PlainTag:
    __slots__ = ("id", "name")

    def __init__(self, id, name):
        self.id = id
        self.name = name


class PlainPet:
    __slots__ = ("id", "name", "category", "photo_urls", "tags", "status", "homepage_url",
                 "thumbnail_ref", "owner_email", "weight_kg", "nickname")


def plain_pet(o):
    p = PlainPet()
    p.id = o.get("id")
    p.name = o["name"]
    c = o.get("category")
    p.category = None if c is None else PlainTag(c.get("id"), c.get("name"))
    p.photo_urls = set(o["photoUrls"])
    p.tags = None if "tags" not in o else [PlainTag(t.get("id"), t.get("name")) for t in o["tags"]]
    p.status = o.get("status")
    p.homepage_url = o.get("homepageUrl")
    p.thumbnail_ref = o.get("thumbnailRef")
    p.owner_email = o.get("ownerEmail")
    p.weight_kg = o.get("weightKg")
    p.nickname = o.get("nickname")
    return p


def plain_pet_dict(p):
    d = {"id": p.id, "name": p.name}
    if p.category is not None:
        d["category"] = {"id": p.category.id, "name": p.category.name}
    d["photoUrls"] = sorted(p.photo_urls)
    if p.tags is not None:
        d["tags"] = [{"id": t.id, "name": t.name} for t in p.tags]
    for key, value in (("status", p.status), ("homepageUrl", p.homepage_url), ("thumbnailRef", p.thumbnail_ref),
                       ("ownerEmail", p.owner_email), ("weightKg", p.weight_kg), ("nickname", p.nickname)):
        if value is not None:
            d[key] = value
    return d


def plain_decode(model, s, loads):
    o = loads(s)
    if model == "small":
        return PlainTag(o.get("id"), o.get("name"))
    if model == "medium":
        return plain_pet(o)
    return [plain_pet(x) for x in o]


def plain_encode(model, v, dumps):
    if model == "small":
        return dumps({"id": v.id, "name": v.name})
    if model == "medium":
        return dumps(plain_pet_dict(v))
    return dumps([plain_pet_dict(x) for x in v])


KLASS = {"small": Tag, "medium": Pet, "large": List[Pet]}
KLASS_NAME = {"small": "Tag", "medium": "Pet", "large": "List[Pet]"}


def pyd_dump(v):
    if isinstance(v, list):
        return "[" + ",".join(x.model_dump_json(by_alias=True, exclude_none=True) for x in v) + "]"
    return v.model_dump_json(by_alias=True, exclude_none=True)


def pyd_load(model, s):
    if model == "large":
        return [Pet.model_validate(x) for x in orjson.loads(s)]
    return KLASS[model].model_validate_json(s)


IMPLS = {
    "generated": (
        lambda m, s: serializer.deserialize(s, KLASS_NAME[m]),
        lambda m, v: serializer.serialize(v),
    ),
    "pydantic": (pyd_load, lambda m, v: pyd_dump(v)),
    "manual": (
        lambda m, s: plain_decode(m, s, json.loads),
        lambda m, v: plain_encode(m, v, lambda d: json.dumps(d, separators=(",", ":"))),
    ),
    "orjson": (
        lambda m, s: plain_decode(m, s, orjson.loads),
        lambda m, v: plain_encode(m, v, lambda d: orjson.dumps(d).decode()),
    ),
    "jsonfloor": (lambda m, s: json.loads(s), None),
}


def time_it(fn):
    for _ in range(20):
        fn()
    n = 1
    while True:
        t0 = time.perf_counter()
        for _ in range(n):
            fn()
        dt = time.perf_counter() - t0
        if dt > 0.05:
            n = max(1, int(n * TARGET_S / dt))
            break
        n *= 4
    gc.collect()
    t0 = time.perf_counter()
    for _ in range(n):
        fn()
    return (time.perf_counter() - t0) * 1e9 / n


def check():
    for m, text in TEXT.items():
        want = json.loads(text)
        got = json.loads(serializer.serialize(serializer.deserialize(text, KLASS_NAME[m])))
        if m == "medium":
            got["photoUrls"].sort()
            want["photoUrls"].sort()
        if m != "large" and got != want:
            print("MISMATCH generated", m, got, want, file=__import__("sys").stderr)


check()
for _ in range(ROUNDS):
    for impl, (dec, enc) in IMPLS.items():
        for model in ("small", "medium", "large"):
            s = TEXT[model]
            print("python", impl, model, "decode", f"{time_it(lambda: dec(model, s)):.1f}", "-", "-")
            if enc is not None:
                value = dec(model, s)
                print("python", impl, model, "encode", f"{time_it(lambda: enc(model, value)):.1f}", "-", "-")
