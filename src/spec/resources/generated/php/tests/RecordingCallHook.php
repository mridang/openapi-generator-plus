<?php

declare(strict_types=1);

namespace PetstoreClient\Test;

use PetstoreClient\CallHook;
use PetstoreClient\CallInfo;
use PetstoreClient\CallObserver;
use PetstoreClient\CallOutcome;

/**
 * A call hook that records what it is told and can edit the headers or fail on request.
 */
final class RecordingCallHook implements CallHook
{
    /** @var list<CallInfo> */
    public array $infos = [];

    /** @var list<array<string, string>> */
    public array $headersSeen = [];

    /** @var list<CallOutcome> */
    public array $outcomes = [];

    /** @var list<string> */
    public array $events = [];

    /**
     * @param (\Closure(array<string, string>): array<string, string>)|null $edit        returns the edited headers
     * @param \Throwable|null                                              $failOnStart  thrown by start
     * @param \Throwable|null                                              $failOnFinish thrown by finish
     * @param bool                                                         $observe      whether start returns an observer
     * @param string                                                       $name         label used in the event log
     * @param \ArrayObject<int, string>|null                               $log          event log shared with other hooks
     */
    public function __construct(
        public ?\Closure $edit = null,
        public ?\Throwable $failOnStart = null,
        public ?\Throwable $failOnFinish = null,
        public bool $observe = true,
        public string $name = 'hook',
        public ?\ArrayObject $log = null,
    ) {
    }

    public function record(string $event): void
    {
        $this->events[] = $event;
        $this->log?->append($event);
    }

    public function start(CallInfo $info, array &$headers): ?CallObserver
    {
        $this->record('start:' . $this->name);
        if ($this->failOnStart instanceof \Throwable) {
            throw $this->failOnStart;
        }
        $this->infos[] = $info;
        $this->headersSeen[] = $headers;
        if ($this->edit instanceof \Closure) {
            $headers = ($this->edit)($headers);
        }
        if (!$this->observe) {
            return null;
        }

        return new readonly class ($this) implements CallObserver {
            public function __construct(private RecordingCallHook $hook)
            {
            }

            public function finish(CallOutcome $outcome): void
            {
                $this->hook->record('finish:' . $this->hook->name);
                $this->hook->outcomes[] = $outcome;
                if ($this->hook->failOnFinish instanceof \Throwable) {
                    throw $this->hook->failOnFinish;
                }
            }
        };
    }
}
