<?php

declare(strict_types=1);

use PetstoreClient\CallHookChain;
use PetstoreClient\CallInfo;
use PetstoreClient\CallOutcome;
use PetstoreClient\Test\RecordingCallHook;

function chainTestInfo(): CallInfo
{
    return new CallInfo('listThings', 'GET', '/things', 'https://api.example.com/things', 'api.example.com');
}

test('bhv hooks 020 chain starts the hooks in order and finishes their observers in reverse order', function (): void {
    /** @var \ArrayObject<int, string> $log */
    $log = new \ArrayObject();
    $first = new RecordingCallHook(name: 'a', log: $log);
    $second = new RecordingCallHook(name: 'b', log: $log);
    $third = new RecordingCallHook(name: 'c', log: $log);
    $headers = [];

    $observer = CallHookChain::chain($first, $second, $third)->start(chainTestInfo(), $headers);
    expect($observer)->not->toBeNull();
    $observer?->finish(new CallOutcome(200, [], null));

    expect($log->getArrayCopy())->toBe(['start:a', 'start:b', 'start:c', 'finish:c', 'finish:b', 'finish:a']);
});

test('bhv langphp 068 chain skips a hook that returns nothing and still finishes the others', function (): void {
    /** @var \ArrayObject<int, string> $log */
    $log = new \ArrayObject();
    $first = new RecordingCallHook(name: 'a', log: $log);
    $silent = new RecordingCallHook(observe: false, name: 'silent', log: $log);
    $last = new RecordingCallHook(name: 'z', log: $log);
    $headers = [];

    $observer = (new CallHookChain($first, $silent, $last))->start(chainTestInfo(), $headers);
    $observer?->finish(new CallOutcome(200, [], null));

    expect($log->getArrayCopy())->toBe(['start:a', 'start:silent', 'start:z', 'finish:z', 'finish:a']);
});

test('bhv langphp 069 chain returns no observer when no hook returns one', function (): void {
    $headers = [];

    $observer = CallHookChain::chain(new RecordingCallHook(observe: false))->start(chainTestInfo(), $headers);

    expect($observer)->toBeNull();
});

test('bhv langphp 070 chain hands the headers one hook edited to the next hook', function (): void {
    $addOne = new RecordingCallHook(edit: static fn (array $headers): array => $headers + ['X-One' => '1']);
    $addTwo = new RecordingCallHook(edit: static fn (array $headers): array => $headers + ['X-Two' => '2']);
    $headers = ['X-Base' => 'b'];

    CallHookChain::chain($addOne, $addTwo)->start(chainTestInfo(), $headers);

    expect($headers)->toBe(['X-Base' => 'b', 'X-One' => '1', 'X-Two' => '2']);
    expect($addTwo->headersSeen[0])->toBe(['X-Base' => 'b', 'X-One' => '1']);
});
