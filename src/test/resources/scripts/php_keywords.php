<?php
$candidates = [
    'abstract', 'and', 'array', 'as', 'break', 'callable', 'case', 'catch',
    'class', 'clone', 'const', 'continue', 'declare', 'default', 'die', 'do',
    'echo', 'else', 'elseif', 'empty', 'enddeclare', 'endfor', 'endforeach',
    'endif', 'endswitch', 'endwhile', 'eval', 'exit', 'extends', 'final',
    'finally', 'fn', 'for', 'foreach', 'function', 'global', 'goto', 'if',
    'implements', 'include', 'include_once', 'instanceof', 'insteadof',
    'interface', 'isset', 'list', 'match', 'namespace', 'new', 'or', 'print',
    'private', 'protected', 'public', 'readonly', 'require', 'require_once',
    'return', 'static', 'switch', 'throw', 'trait', 'try', 'unset', 'use',
    'var', 'while', 'xor', 'yield',
];

$keywords = [];
foreach ($candidates as $word) {
    $tokens = token_get_all("<?php $word ");
    // Skip the T_OPEN_TAG token and check if the candidate is NOT tokenized as T_STRING
    // If it's not T_STRING, it's a keyword/reserved word
    foreach ($tokens as $token) {
        if (is_array($token) && $token[1] === $word && $token[0] !== T_STRING) {
            $keywords[] = $word;
            break;
        }
    }
}

sort($keywords);
foreach ($keywords as $kw) {
    echo $kw . "\n";
}
