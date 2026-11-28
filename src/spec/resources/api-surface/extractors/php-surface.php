<?php

/*
 * Prints the public API records of the PHP SDK in /work (see AbstractApiSurfaceSpec).
 *
 * It loads every class the composer autoloader maps to lib/ and reads it with PHP's Reflection,
 * plus the docblock types through phpDocumentor's reflection-docblock, which the SDK already
 * depends on, so nothing is fetched:
 *
 *   php /tmp/api-surface/php-surface.php
 *
 * @@S  one line per public declaration: the class, interface, trait or enum with its parent and
 *      interfaces, then every public or protected constant, enum case, property and method as
 *      `<Class>::<member>`, with native types and, where the docblock declares one, the
 *      docblock type in `[doc ...]`. Every class name is fully qualified, so a type of another
 *      package shows up by its namespace (`Psr\`, `Symfony\`, `Brick\Math\`, ...). A class or
 *      member tagged @internal is left out, the way phpDocumentor leaves it out of the docs.
 * @@T  every class that is not @internal, and every @internal class a listed line names.
 * @@E  the same: every class of the autoload map is exported by construction.
 * @@I  an @internal class that a listed line names, so a caller sees it anyway.
 */

declare(strict_types=1);

$root = getcwd();
require $root . '/vendor/autoload.php';

use phpDocumentor\Reflection\DocBlockFactory;
use phpDocumentor\Reflection\Types\ContextFactory;

$composer = json_decode((string) file_get_contents($root . '/composer.json'), true);
$prefixes = $composer['autoload']['psr-4'] ?? [];

$classes = [];
foreach ($prefixes as $prefix => $dir) {
    $base = rtrim($root . '/' . $dir, '/');
    $it = new RecursiveIteratorIterator(new RecursiveDirectoryIterator($base, FilesystemIterator::SKIP_DOTS));
    $files = [];
    foreach ($it as $file) {
        if ($file->getExtension() === 'php') {
            $files[] = $file->getPathname();
        }
    }
    sort($files);
    foreach ($files as $path) {
        $rel = substr($path, strlen($base) + 1, -4);
        $name = $prefix . str_replace('/', '\\', $rel);
        if (class_exists($name) || interface_exists($name) || trait_exists($name) || enum_exists($name)) {
            $classes[] = $name;
        }
    }
}
sort($classes);

$factory = DocBlockFactory::createInstance();
$contexts = new ContextFactory();
$signatures = [];
$records = [];
$internal = [];

function isInternal(string|false $doc): bool
{
    return $doc !== false && preg_match('/^\s*\*\s*@internal\b/m', $doc) === 1;
}

function typeText(?ReflectionType $type): string
{
    return $type === null ? '' : (string) $type;
}

function docType(string $text): string
{
    return preg_replace('/(^|[^\w\\\\])\\\\/', '$1', preg_replace('/\s+/', ' ', trim($text)));
}

function valueText(mixed $value): string
{
    if (is_object($value)) {
        if ($value instanceof UnitEnum) {
            return get_class($value) . '::' . $value->name;
        }
        return 'new ' . get_class($value);
    }
    $text = var_export($value, true);
    return preg_replace('/\s+/', ' ', str_replace(["array (\n", ",\n)"], ['[', ']'], $text));
}

/** The docblock tags of a reflector, or null when it has none or it cannot be read. */
function docTags(ReflectionClass $class, Reflector $reflector): ?phpDocumentor\Reflection\DocBlock
{
    global $factory, $contexts;
    $doc = $reflector->getDocComment();
    if ($doc === false) {
        return null;
    }
    try {
        return $factory->create($doc, $contexts->createFromReflector($class));
    } catch (Throwable) {
        return null;
    }
}

function paramText(ReflectionParameter $p): string
{
    $text = '';
    $type = typeText($p->getType());
    if ($type !== '') {
        $text .= $type . ' ';
    }
    if ($p->isPassedByReference()) {
        $text .= '&';
    }
    if ($p->isVariadic()) {
        $text .= '...';
    }
    $text .= '$' . $p->getName();
    if ($p->isDefaultValueAvailable()) {
        if ($p->isDefaultValueConstant()) {
            $text .= ' = ' . $p->getDefaultValueConstantName();
        } else {
            try {
                $text .= ' = ' . valueText($p->getDefaultValue());
            } catch (Throwable) {
                $text .= ' = ?';
            }
        }
    }
    if ($p->isPromoted()) {
        $text = 'promoted ' . $text;
    }
    return $text;
}

function visibility(ReflectionMethod|ReflectionProperty|ReflectionClassConstant $m): string
{
    $words = [];
    if ($m instanceof ReflectionMethod && $m->isAbstract()) {
        $words[] = 'abstract';
    }
    if (method_exists($m, 'isFinal') && $m->isFinal()) {
        $words[] = 'final';
    }
    $words[] = $m->isProtected() ? 'protected' : 'public';
    if (!($m instanceof ReflectionClassConstant) && $m->isStatic()) {
        $words[] = 'static';
    }
    if ($m instanceof ReflectionProperty && $m->isReadOnly()) {
        $words[] = 'readonly';
    }
    return implode(' ', $words);
}

function members(string $owner, ReflectionClass $class, ReflectionClass $source): void
{
    global $signatures;
    $isEnum = $class->isEnum();
    foreach ($source->getReflectionConstants() as $c) {
        if ($c->getDeclaringClass()->getName() !== $source->getName() || $c->isPrivate()) {
            continue;
        }
        if (isInternal($c->getDocComment())) {
            continue;
        }
        if ($isEnum && $c->isEnumCase()) {
            $case = (new ReflectionEnum($class->getName()))->getCase($c->getName());
            $value = $case instanceof ReflectionEnumBackedCase ? ' = ' . valueText($case->getBackingValue()) : '';
            $signatures[] = "$owner::case {$c->getName()}$value";
            continue;
        }
        $signatures[] = "$owner::" . visibility($c) . " const {$c->getName()} = " . valueText($c->getValue());
    }
    foreach ($source->getProperties() as $p) {
        if ($p->getDeclaringClass()->getName() !== $source->getName() || $p->isPrivate()) {
            continue;
        }
        if (isInternal($p->getDocComment())) {
            continue;
        }
        $line = "$owner::" . visibility($p) . ' ' . (typeText($p->getType()) !== '' ? typeText($p->getType()) . ' ' : '') . '$' . $p->getName();
        $doc = docTags($source, $p);
        if ($doc !== null) {
            foreach ($doc->getTagsByName('var') as $tag) {
                if (method_exists($tag, 'getType') && $tag->getType() !== null) {
                    $line .= ' [doc ' . docType((string) $tag->getType()) . ']';
                }
            }
        }
        $signatures[] = $line;
    }
    foreach ($source->getMethods() as $m) {
        if ($m->getDeclaringClass()->getName() !== $source->getName() || $m->isPrivate()) {
            continue;
        }
        if (isInternal($m->getDocComment())) {
            continue;
        }
        $params = implode(', ', array_map('paramText', $m->getParameters()));
        $ret = $m->hasReturnType() ? ': ' . typeText($m->getReturnType()) : '';
        $line = "$owner::" . visibility($m) . " function {$m->getName()}($params)$ret";
        $doc = docTags($source, $m);
        if ($doc !== null) {
            $docs = [];
            foreach ($doc->getTagsByName('param') as $tag) {
                if (method_exists($tag, 'getType') && $tag->getType() !== null) {
                    $docs[] = '$' . $tag->getVariableName() . ': ' . docType((string) $tag->getType());
                }
            }
            foreach ($doc->getTagsByName('return') as $tag) {
                if (method_exists($tag, 'getType') && $tag->getType() !== null) {
                    $docs[] = 'return ' . docType((string) $tag->getType());
                }
            }
            foreach ($doc->getTagsByName('throws') as $tag) {
                if (method_exists($tag, 'getType') && $tag->getType() !== null) {
                    $docs[] = 'throws ' . docType((string) $tag->getType());
                }
            }
            if ($docs !== []) {
                $line .= ' [doc ' . implode('; ', $docs) . ']';
            }
        }
        $signatures[] = $line;
    }
}

foreach ($classes as $name) {
    $class = new ReflectionClass($name);
    if (isInternal($class->getDocComment())) {
        $internal[$name] = true;
        continue;
    }
    $records[] = "@@T $name";
    $records[] = "@@E $name";
    $kind = $class->isEnum() ? 'enum' : ($class->isInterface() ? 'interface' : ($class->isTrait() ? 'trait' : 'class'));
    $words = [];
    if ($kind === 'class' && $class->isAbstract()) {
        $words[] = 'abstract';
    }
    if ($class->isFinal() && $kind === 'class') {
        $words[] = 'final';
    }
    if ($kind === 'class' && $class->isReadOnly()) {
        $words[] = 'readonly';
    }
    $words[] = $kind;
    $line = implode(' ', $words) . ' ' . $name;
    if ($kind === 'enum') {
        $backing = (new ReflectionEnum($name))->getBackingType();
        if ($backing !== null) {
            $line .= ': ' . $backing;
        }
    }
    $parent = $class->getParentClass();
    if ($parent !== false) {
        $line .= ' extends ' . $parent->getName();
    }
    $interfaces = $class->getInterfaceNames();
    if ($kind === 'enum') {
        $interfaces = array_values(array_diff($interfaces, ['UnitEnum', 'BackedEnum']));
    }
    sort($interfaces);
    if ($interfaces !== []) {
        $line .= ($kind === 'interface' ? ' extends ' : ' implements ') . implode(', ', $interfaces);
    }
    $traits = $class->getTraitNames();
    sort($traits);
    if ($traits !== []) {
        $line .= ' uses ' . implode(', ', $traits);
    }
    $signatures[] = $line;
    members($name, $class, $class);
    // The members inherited from an @internal parent: a caller reaches them only through this class.
    for ($p = $class->getParentClass(); $p !== false; $p = $p->getParentClass()) {
        if (isset($internal[$p->getName()]) || isInternal($p->getDocComment())) {
            members($name, $class, $p);
        }
    }
}

$signatures = array_values(array_unique($signatures));
foreach (array_keys($internal) as $name) {
    $pattern = '/(^|[^\w\\\\])' . preg_quote($name, '/') . '($|[^\w\\\\])/';
    foreach ($signatures as $s) {
        if (preg_match($pattern, $s) === 1) {
            $records[] = "@@T $name";
            $records[] = "@@E $name";
            $records[] = "@@I $name";
            break;
        }
    }
}
foreach ($signatures as $s) {
    $records[] = "@@S $s";
}
$records = array_values(array_unique($records));
sort($records, SORT_STRING);
foreach ($records as $r) {
    echo $r, "\n";
}
