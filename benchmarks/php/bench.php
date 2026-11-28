<?php

/*
 * Runs against the generated php SDK. Prints rows:
 * lang impl model op ns_per_op bytes_per_op allocs_per_op (one per round; bytes/allocs are "-" on php).
 */

declare(strict_types=1);

require __DIR__ . '/vendor/autoload.php';

use PetstoreClient\Models\Category;
use PetstoreClient\Models\Pet;
use PetstoreClient\Models\PetStatusEnum;
use PetstoreClient\Models\Tag;
use PetstoreClient\ObjectSerializer;

$rounds = (int) (getenv('ROUNDS') ?: 5);
$targetS = ((int) (getenv('TARGET_MS') ?: 700)) / 1000;
$text = [];
foreach (['small', 'medium', 'large'] as $m) {
    $text[$m] = file_get_contents("/fixtures/$m.json");
}
$sink = null;

function mapPet(array $o): Pet
{
    $tags = null;
    if (isset($o['tags'])) {
        $tags = new \Ds\Vector(array_map(fn ($t) => new Tag($t['id'] ?? null, $t['name'] ?? null), $o['tags']));
    }
    return new Pet(
        $o['name'],
        new \Ds\Set($o['photoUrls']),
        $o['id'] ?? null,
        isset($o['category']) ? new Category($o['category']['id'] ?? null, $o['category']['name'] ?? null) : null,
        $tags,
        isset($o['status']) ? PetStatusEnum::from($o['status']) : null,
        null,
        $o['homepageUrl'] ?? null,
        $o['thumbnailRef'] ?? null,
        null,
        $o['ownerEmail'] ?? null,
        isset($o['weightKg']) ? (float) $o['weightKg'] : null,
        $o['nickname'] ?? null,
    );
}

function petArray(Pet $p): array
{
    $a = ['id' => $p->id, 'name' => $p->name];
    if ($p->category !== null) {
        $a['category'] = ['id' => $p->category->id, 'name' => $p->category->name];
    }
    $urls = $p->photoUrls->toArray();
    sort($urls);
    $a['photoUrls'] = $urls;
    if ($p->tags !== null) {
        $a['tags'] = array_map(fn ($t) => ['id' => $t->id, 'name' => $t->name], $p->tags->toArray());
    }
    foreach (['status' => $p->status?->value, 'homepageUrl' => $p->homepageUrl, 'thumbnailRef' => $p->thumbnailRef,
        'ownerEmail' => $p->ownerEmail, 'weightKg' => $p->weightKg, 'nickname' => $p->nickname] as $k => $v) {
        if ($v !== null) {
            $a[$k] = $v;
        }
    }
    return $a;
}

$classes = ['small' => Tag::class, 'medium' => Pet::class, 'large' => 'Ds\Vector<Pet>'];
$flags = JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE;
$impls = [
    'generated' => [
        fn (string $m, string $s) => ObjectSerializer::deserialize($s, $classes[$m]),
        fn (string $m, mixed $v) => ObjectSerializer::serialize($v),
    ],
    'manual' => [
        function (string $m, string $s) {
            $o = json_decode($s, true);
            return match ($m) {
                'small' => new Tag($o['id'] ?? null, $o['name'] ?? null),
                'medium' => mapPet($o),
                default => new \Ds\Vector(array_map('mapPet', $o)),
            };
        },
        function (string $m, mixed $v) use ($flags) {
            return json_encode(match ($m) {
                'small' => ['id' => $v->id, 'name' => $v->name],
                'medium' => petArray($v),
                default => array_map('petArray', $v->toArray()),
            }, $flags);
        },
    ],
    'jsonfloor' => [fn (string $m, string $s) => json_decode($s, true), null],
];

function timeIt(callable $fn, float $targetS): float
{
    for ($i = 0; $i < 20; $i++) {
        $fn();
    }
    $n = 1;
    while (true) {
        $t0 = hrtime(true);
        for ($i = 0; $i < $n; $i++) {
            $fn();
        }
        $dt = (hrtime(true) - $t0) / 1e9;
        if ($dt > 0.05) {
            $n = max(1, (int) ($n * $targetS / $dt));
            break;
        }
        $n *= 4;
    }
    $t0 = hrtime(true);
    for ($i = 0; $i < $n; $i++) {
        $fn();
    }
    return (hrtime(true) - $t0) / $n;
}

foreach (['small', 'medium'] as $m) {
    $back = json_decode(ObjectSerializer::serialize(ObjectSerializer::deserialize($text[$m], $classes[$m])), true);
    $want = json_decode($text[$m], true);
    if (isset($back['photoUrls'])) {
        sort($back['photoUrls']);
        sort($want['photoUrls']);
    }
    if ($back != $want) {
        fwrite(STDERR, "MISMATCH generated $m\n");
    }
}

for ($r = 0; $r < $rounds; $r++) {
    foreach ($impls as $name => [$dec, $enc]) {
        foreach (['small', 'medium', 'large'] as $m) {
            $s = $text[$m];
            printf("php %s %s decode %.1f - -\n", $name, $m, timeIt(fn () => $dec($m, $s), $targetS));
            if ($enc !== null) {
                $v = $dec($m, $s);
                printf("php %s %s encode %.1f - -\n", $name, $m, timeIt(fn () => $enc($m, $v), $targetS));
            }
        }
    }
}
