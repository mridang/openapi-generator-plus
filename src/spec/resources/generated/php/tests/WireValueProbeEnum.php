<?php

declare(strict_types=1);

namespace PetstoreClient\Test;

/*
 * A stand-in backed enum for the wire value tests: declared in its own file, one class per file,
 * because Composer's strict PSR-4 autoload-dev rule (PetstoreClient\Test\ => tests/) refuses a
 * class declared in a file with another name.
 */
enum WireValueProbeEnum: string
{
    case Open = 'open-wire';
}
