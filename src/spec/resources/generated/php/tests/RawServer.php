<?php

declare(strict_types=1);

namespace PetstoreClient\Test;

/**
 * A scripted HTTP/1.1 server on a raw socket, run as a child php process, so a test can stall a body,
 * cut it short, hold it open or count the requests, which neither the shared mock server nor a plain
 * HTTP server can do. The script is the body of a function that gets `$socket` (the accepted
 * connection), `$hit` (1 for the first connection) and `$head` (the request head), and writes the
 * reply with `reply()`, `chunk()` and `endChunks()`. Leaving the script closes the connection, so a
 * script that returns early cuts the body short. `awaitRelease()` blocks the script until the test
 * calls {@see release()}, `holdUntilGone()` waits silently until the client drops the connection, and `gone()` tells the test that the client dropped the connection.
 * `recordBody()` reads the request body and {@see body()} hands it to the test. Connections are served
 * one at a time.
 */
final class RawServer
{
    private const string PRELUDE = <<<'PHP_WRAP'
    <?php
    declare(strict_types=1);
    $dir = $argv[1];
    $listener = stream_socket_server('tcp://127.0.0.1:0', $errno, $errstr);
    if ($listener === false) {
        exit(1);
    }
    $name = (string) stream_socket_get_name($listener, false);
    file_put_contents($dir . '/port', substr($name, (int) strrpos($name, ':') + 1));
    
    /** @param resource $socket */
    function reply($socket, int $status, string ...$headers): void
    {
        $head = 'HTTP/1.1 ' . $status . " Test\r\n";
        foreach ($headers as $header) {
            $head .= $header . "\r\n";
        }
        @fwrite($socket, $head . "Connection: close\r\n\r\n");
    }
    
    /** @param resource $socket */
    function chunk($socket, string $data): bool
    {
        $written = @fwrite($socket, dechex(strlen($data)) . "\r\n" . $data . "\r\n");
    
        return $written !== false && $written > 0;
    }
    
    /** @param resource $socket */
    function endChunks($socket): void
    {
        @fwrite($socket, "0\r\n\r\n");
    }
    
    function awaitRelease(): void
    {
        global $dir;
        $until = microtime(true) + 20.0;
        while (!file_exists($dir . '/release') && microtime(true) < $until) {
            usleep(10_000);
        }
    }
    
    /**
     * Holds the connection open and silent until the client drops it (or the test releases the
     * script), so that a script waiting here does not keep the next connection from being served.
     *
     * @param resource $socket
     */
    function holdUntilGone($socket): void
    {
        global $dir;
        $until = microtime(true) + 20.0;
        while (!file_exists($dir . '/release') && microtime(true) < $until) {
            $read = [$socket];
            $write = null;
            $except = null;
            if (@stream_select($read, $write, $except, 0, 50_000) > 0) {
                $data = @fread($socket, 1024);
                if (($data === '' || $data === false) && feof($socket)) {
                    return;
                }
            }
        }
    }
    
    /**
     * Reads the body of the request that the head announces (a Content-Length or a chunked
     * body), saves it for the test and returns it. Answers `Expect: 100-continue` first.
     *
     * @param resource $socket
     */
    function recordBody($socket, string $head, int $hit): string
    {
        global $dir;
        if (stripos($head, 'expect: 100-continue') !== false) {
            @fwrite($socket, "HTTP/1.1 100 Continue\r\n\r\n");
        }
        $body = '';
        if (preg_match('/^transfer-encoding:\s*chunked/im', $head) === 1) {
            while (($size = stream_get_line($socket, 64, "\r\n")) !== false) {
                $length = (int) hexdec(trim(explode(';', $size)[0]));
                if ($length === 0) {
                    stream_get_line($socket, 64, "\r\n");
                    break;
                }
                $read = '';
                while (strlen($read) < $length) {
                    $part = fread($socket, $length - strlen($read));
                    if ($part === false || $part === '') {
                        break;
                    }
                    $read .= $part;
                }
                $body .= $read;
                stream_get_line($socket, 64, "\r\n");
            }
        } elseif (preg_match('/^content-length:\s*(\d+)/im', $head, $match) === 1) {
            $want = (int) $match[1];
            while (strlen($body) < $want) {
                $part = fread($socket, $want - strlen($body));
                if ($part === false || $part === '') {
                    break;
                }
                $body .= $part;
            }
        }
        file_put_contents($dir . '/body.' . $hit, $body);
    
        return $body;
    }
    
    /** The base URL of this server, so that a reply can point back at it. */
    function selfUrl(): string
    {
        global $name;
    
        return 'http://127.0.0.1:' . substr($name, (int) strrpos($name, ':') + 1);
    }
    
    /**
     * The decoded query of the request that the head describes.
     *
     * @return array<string, mixed>
     */
    function queryOf(string $head): array
    {
        $target = explode(' ', $head)[1] ?? '/';
        parse_str((string) parse_url($target, PHP_URL_QUERY), $query);
    
        return $query;
    }
    
    /** @param resource $socket */
    function replyJson($socket, int $status, string $json, string ...$headers): void
    {
        reply($socket, $status, 'Content-Type: application/json', 'Content-Length: ' . strlen($json), ...$headers);
        @fwrite($socket, $json);
    }
    
    function gone(): void
    {
        global $dir;
        touch($dir . '/gone');
    }
    
    $hit = 0;
    while (true) {
        $socket = @stream_socket_accept($listener, 60.0);
        if ($socket === false) {
            exit(0);
        }
        $head = (string) stream_get_line($socket, 65536, "\r\n\r\n");
        $hit++;
        file_put_contents($dir . '/heads', base64_encode($head) . "\n", FILE_APPEND);
        $script = static function ($socket, int $hit, string $head): void {
    PHP_WRAP;

    private const string EPILOGUE = <<<'PHP'

            };
            $script($socket, $hit, $head);
            fclose($socket);
        }
        PHP;

    /** @var resource */
    private $process;

    /**
     * @param resource $process the child php process that serves the connections
     */
    private function __construct(private readonly string $dir, private readonly int $port, $process)
    {
        $this->process = $process;
    }

    /**
     * Starts a server that runs $script for every connection it accepts.
     *
     * @param string $script the body of the function that serves one connection
     */
    public static function serve(string $script): self
    {
        $base = (string) tempnam(sys_get_temp_dir(), 'rawserver_');
        @unlink($base);
        $dir = $base . '.d';
        mkdir($dir);
        file_put_contents($dir . '/server.php', self::PRELUDE . "\n" . $script . self::EPILOGUE);
        $process = proc_open(
            [PHP_BINARY, $dir . '/server.php', $dir],
            [1 => ['file', '/dev/null', 'w'], 2 => ['file', $dir . '/stderr', 'w']],
            $pipes
        );
        if (!is_resource($process)) {
            throw new \RuntimeException('could not start the raw test server');
        }
        $until = microtime(true) + 10.0;
        while (!file_exists($dir . '/port') && microtime(true) < $until) {
            usleep(10_000);
        }
        $port = (int) @file_get_contents($dir . '/port');
        if ($port === 0) {
            throw new \RuntimeException('the raw test server did not start: ' . @file_get_contents($dir . '/stderr'));
        }

        return new self($dir, $port, $process);
    }

    /** The URL of a path on this server. */
    public function url(string $path = '/'): string
    {
        return 'http://127.0.0.1:' . $this->port . $path;
    }

    /** How many connections the server has served. */
    public function hits(): int
    {
        return count($this->heads());
    }

    /**
     * The request head of every connection, in order.
     *
     * @return list<string>
     */
    public function heads(): array
    {
        if (!is_file($this->dir . '/heads')) {
            return [];
        }
        $lines = file($this->dir . '/heads', FILE_IGNORE_NEW_LINES | FILE_SKIP_EMPTY_LINES);
        if ($lines === false) {
            return [];
        }

        return array_map(static fn (string $line): string => (string) base64_decode($line, true), $lines);
    }

    /**
     * The request body a script read with recordBody() for a connection (1 for the first).
     */
    public function body(int $hit = 1): string
    {
        $file = $this->dir . '/body.' . $hit;
        $until = microtime(true) + 5.0;
        while (!file_exists($file) && microtime(true) < $until) {
            usleep(10_000);
        }

        return (string) @file_get_contents($file);
    }

    /** Lets a script that waits in awaitRelease() carry on. */
    public function release(): void
    {
        touch($this->dir . '/release');
    }

    /**
     * The path of the request of a connection (0 for the first), without its query.
     */
    public function path(int $connection = 0): string
    {
        return (string) parse_url(explode(' ', $this->heads()[$connection])[1] ?? '/', PHP_URL_PATH);
    }

    /**
     * The decoded query of the request of a connection (0 for the first).
     *
     * @return array<string, mixed>
     */
    public function query(int $connection = 0): array
    {
        parse_str((string) parse_url(explode(' ', $this->heads()[$connection])[1] ?? '/', PHP_URL_QUERY), $query);

        return array_combine(array_map(strval(...), array_keys($query)), array_values($query));
    }

    /**
     * A header of the request of a connection (0 for the first), or null when it was not sent.
     */
    public function header(string $name, int $connection = 0): ?string
    {
        foreach (explode("\r\n", $this->heads()[$connection]) as $line) {
            if (strncasecmp($line, $name . ':', strlen($name) + 1) === 0) {
                return trim(substr($line, strlen($name) + 1));
            }
        }

        return null;
    }

    /** Waits until a script reported that the client dropped the connection. */
    public function awaitClientGone(float $seconds = 10.0): bool
    {
        $until = microtime(true) + $seconds;
        while (microtime(true) < $until) {
            if (file_exists($this->dir . '/gone')) {
                return true;
            }
            usleep(10_000);
        }

        return false;
    }

    public function __destruct()
    {
        $this->release();
        proc_terminate($this->process);
        proc_close($this->process);
        foreach ((array) glob($this->dir . '/*') as $file) {
            @unlink((string) $file);
        }
        @rmdir($this->dir);
    }
}
