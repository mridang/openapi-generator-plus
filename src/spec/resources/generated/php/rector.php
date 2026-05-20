<?php

declare(strict_types=1);

use Rector\Config\RectorConfig;
use Rector\Set\ValueObject\SetList;

return RectorConfig::configure()
    ->withPaths([__DIR__])
    ->withSets([
        SetList::CODE_QUALITY,
        SetList::DEAD_CODE,
        SetList::EARLY_RETURN,
        SetList::TYPE_DECLARATION,
    ])
    ->withSkip([
        __DIR__ . '/vendor',
        // Mustache cannot produce PHP string interpolation ("$var") syntax
        \Rector\CodeQuality\Rector\Concat\JoinStringConcatRector::class,
        // Temp variables needed for PHPStan @var type assertions
        \Rector\DeadCode\Rector\Assign\RemoveUnusedVariableAssignRector::class,
    ]);
