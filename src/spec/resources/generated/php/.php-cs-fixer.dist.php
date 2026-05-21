<?php

declare(strict_types=1);

$finder = (new PhpCsFixer\Finder())
    ->in(__DIR__ . '/lib');

return (new PhpCsFixer\Config())
    ->setRules([
        '@PHP84Migration:risky' => true,
        'fully_qualified_strict_types' => ['import_symbols' => true],
        'no_unused_imports' => true,
        'ordered_imports' => ['sort_algorithm' => 'alpha'],
    ])
    ->setFinder($finder)
    ->setRiskyAllowed(true)
    ->setCacheFile('.phpcsf');
