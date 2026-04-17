<?php

declare(strict_types=1);

use Rector\Config\RectorConfig;
use Rector\Set\ValueObject\SetList;

return RectorConfig::configure()
    ->withPaths([__DIR__ . '/lib', __DIR__ . '/test'])
    ->withSets([
        SetList::CODE_QUALITY,
        SetList::DEAD_CODE,
        SetList::EARLY_RETURN,
        SetList::TYPE_DECLARATION,
    ])
    ->withSkip([
        // Mustache template engine cannot produce string interpolation syntax
        \Rector\CodeQuality\Rector\Concat\JoinStringConcatRector::class,
        // Temp variables needed for PHPStan @var type assertions
        \Rector\DeadCode\Rector\Assign\RemoveUnusedVariableAssignRector::class,
    ]);
