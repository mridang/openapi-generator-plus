<?php

declare(strict_types=1);

$finder = (new PhpCsFixer\Finder())
    ->in(__DIR__ . '/lib');

return (new PhpCsFixer\Config())
    ->setRules([
        '@PHP84Migration' => true,
        '@PHP82Migration:risky' => true,
        // Keep types fully qualified. With import_symbols=true this rule
        // shortened PHPDoc-only types like `\Ds\Vector` to `Vector` WITHOUT
        // adding the `use` (it only imports native typehints, not PHPDoc),
        // which left PHPStan unable to resolve the symbol. The default keeps
        // `\Ds\Vector` fully qualified, which always resolves.
        'fully_qualified_strict_types' => true,
        'no_unused_imports' => true,
        'ordered_imports' => ['sort_algorithm' => 'alpha'],
    ])
    ->setFinder($finder)
    ->setRiskyAllowed(true)
    ->setUsingCache(false);
